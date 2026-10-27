package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.BucketPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsAbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Income under "Capital gains", part by part, after the head's own losses (S2 s.108(2); 1961 s.70(2), (3); D-243).
 *
 * <p>Each bucket's gain is its consideration less its cost less the charges {@link CapitalGainsCharges}' rows deduct
 * (2025 s.72; 1961 s.48; W-162a). Each bucket of {@link CapitalGainsInput} belongs to the {@link CapitalGainsPart} the row of {@code RS-CG-PART} its
 * facts meet names (D-289), its STT read through {@link CapitalGainsStt} (D-298): which gains a special rate charges is the rule data's, and a bucket no row covers is refused
 * rather than charged with total income; whether the head takes a bucket at all is {@link CapitalGainsScope}'s rows'
 * (W-160a). A
 * part whose buckets net to a loss contributes that loss, of the term {@code CG-PARTS} declares for the part: a
 * long-term loss is set off only against the parts that row lets it reach, a short-term loss against any part (D-309). The long-term loss goes first, because it can use fewer parts; each loss
 * takes the parts in {@code SETOFF-WITHIN-CAPITAL-GAINS}'s declared order, which neither Act prescribes (OQ-32), and
 * every set-off line names it. What no part absorbs is shown and stays in the head — a capital-gains loss is not set
 * off against another head (S2 s.109(2); 1961 s.71(3)); it is carried forward (S2 s.111; 1961 s.74; D-247).
 */
public final class CapitalGainsNode implements ComputationNode {

    private static final RuleSetId ROW = RuleSetIds.RS_SETOFF_WITHIN_CAPITAL_GAINS;

    private static final RuleSetId PARTS = RuleSetIds.RS_CG_PART;

    private static final ItemFactId TERM = new ItemFactId("capitalgains.term");

    private static final ItemFactId ASSET = new ItemFactId("capitalgains.asset");

    private static final ItemFactId STT_ON_ACQUISITION = new ItemFactId("capitalgains.stt.acquisition");

    private static final ItemFactId STT_ON_TRANSFER = new ItemFactId("capitalgains.stt.transfer");

    private static final ItemFactId ACQUISITION = new ItemFactId("capitalgains.acquisition");

    private static final ItemFactId CORPORATE_ACTION = new ItemFactId("capitalgains.corporate.action");

    private static final ItemFactId ACQUIRED_ON = new ItemFactId("capitalgains.acquired.on");

    @Override
    public NodeId produces() {
        return NodeIds.CAPITALGAINS_INCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(ROW, PARTS, DeclaredCapitalGainsParts.PARTS, CapitalGainsStt.CHARGEABILITY,
                CapitalGainsTerm.HOLDING_PERIOD, CapitalGainsScope.NOT_COMPUTED, CapitalGainsScope.ELSEWHERE,
                CapitalGainsCharges.DEDUCTIONS);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        GoverningRow row = GoverningRow.of(ctx, ROW);
        CapitalGainsAbsorptionOrder order = RuleDefinitions.require(row.version().definition(),
                CapitalGainsAbsorptionOrder.class, row.id());
        Map<CapitalGainsPart, Money> net = new HashMap<>();
        List<TraceLine> lines = new ArrayList<>();
        for (CapitalGainsInput.Bucket bucket : ctx.inputs().capitalGains().buckets()) {
            Money gain = CapitalGainsCharges.gainOf(ctx, bucket, lines);
            if (!CapitalGainsScope.takes(ctx, bucket, gain, lines)) {
                continue;
            }
            Term term = CapitalGainsTerm.of(ctx, bucket, gain, lines);
            net.merge(partOf(ctx, bucket, gain, term, CapitalGainsStt.of(ctx, bucket, gain, term, lines)), gain,
                    Money::plus);
        }
        DeclaredCapitalGainsParts declared = DeclaredCapitalGainsParts.of(ctx);
        Map<CapitalGainsPart, Money> available = new HashMap<>();
        Money longTermLoss = Money.ZERO;
        Money shortTermLoss = Money.ZERO;
        for (CapitalGainsPart part : order.parts()) {
            Money amount = net.getOrDefault(part, Money.ZERO);
            if (amount.isLessThan(Money.ZERO)) {
                Money loss = Money.ZERO.minus(amount);
                lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.MEASURE, amount, Money.ZERO)
                        .withLimitedBy(part + ": a loss"));
                boolean longTerm = declared.collectsLongTerm(part);
                longTermLoss = longTerm ? longTermLoss.plus(loss) : longTermLoss;
                shortTermLoss = longTerm ? shortTermLoss : shortTermLoss.plus(loss);
            } else if (amount.isGreaterThan(Money.ZERO)) {
                declared.requireDeclared(part);
                lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.INCOME, amount, amount)
                        .withLimitedBy(part.toString()));
                available.put(part, amount);
            }
        }
        CapitalGainsAbsorption parts = new CapitalGainsAbsorption(order, available);
        Money longTermLeft = setOff(ctx, parts, longTermLoss, true, declared, lines);
        Money shortTermLeft = setOff(ctx, parts, shortTermLoss, false, declared, lines);
        CapitalGainsHead head = new CapitalGainsHead(available, shortTermLeft, longTermLeft);
        return new NodeResult(head, lines);
    }

    /**
     * The parts absorb up to {@code loss}, in order — for a long-term loss only those {@code declared} lets it reach
     * (S2 s.108(2)(b)). Returns what is left, which is shown.
     */
    private static Money setOff(NodeContext ctx, CapitalGainsAbsorption parts, Money loss, boolean longTerm,
            DeclaredCapitalGainsParts declared, List<TraceLine> lines) {
        RuleId row = ctx.ruleIdOf(ROW);
        String which = longTerm ? "long-term" : "short-term";
        Predicate<CapitalGainsPart> reaches = longTerm ? declared::canAbsorbLongTermLoss : part -> true;
        Money remaining = loss.minus(parts.absorb(loss, reaches, lines, (sequence, part, gain, taken) -> ctx
                .traceLine(sequence, row, StepKind.SET_OFF, gain, taken).withLimitedBy(which + " loss against " + part
                        + "; order " + parts.order().parts()
                        + ", the declared default: neither Act prescribes one (OQ-32)")));
        if (remaining.isGreaterThan(Money.ZERO)) {
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.MEASURE, loss, remaining).withLimitedBy(which
                    + " loss not set off, and not against another head (S2 s.109(2); 1961 s.71(3)); carried forward"
                    + " (S2 s.111(1); 1961 s.74(1))"));
        }
        return remaining;
    }

    /**
     * The part the one row of {@code RS-CG-PART} the bucket's facts meet names (D-289).
     *
     * @throws IllegalArgumentException if no row covers the bucket — its facts are named, so the gap can be read
     */
    private static CapitalGainsPart partOf(NodeContext ctx, CapitalGainsInput.Bucket bucket, Money gain, Term term,
            CapitalGainsStt.Sides stt) {
        return ctx.optionalRuleOf(PARTS, factsOf(bucket, term, stt))
                .map(row -> RuleDefinitions.require(TemporallyApportionedCap.governingVersion(row,
                        ctx.inputs().temporal()).definition(), BucketPart.class, row.id()).part())
                .orElseThrow(() -> new IllegalArgumentException("Capital gains of " + gain + " ("
                        + term + ", " + bucket.facts() + ", STT as read " + stt + ") fall in no row of "
                        + PARTS + " for "
                        + ctx.pack().taxYear().label() + ", so no provision is known to charge them"));
    }

    /**
     * The bucket's facts as the rows test them, its acquisition date among them; the corporate action only where the lot
     * came from one.
     */
    private static ItemFacts factsOf(CapitalGainsInput.Bucket bucket, Term term, CapitalGainsStt.Sides stt) {
        BucketFacts facts = bucket.facts();
        Map<ItemFactId, String> codes = new HashMap<>();
        codes.put(TERM, term.name());
        codes.put(ASSET, facts.assetKind());
        codes.put(STT_ON_ACQUISITION, stt.acquisition());
        codes.put(STT_ON_TRANSFER, stt.transfer());
        codes.put(ACQUISITION, facts.acquisitionMode());
        facts.corporateAction().ifPresent(action -> codes.put(CORPORATE_ACTION, action));
        return new ItemFacts(codes, Map.of(), Map.of(ACQUIRED_ON, facts.acquiredOn()));
    }
}
