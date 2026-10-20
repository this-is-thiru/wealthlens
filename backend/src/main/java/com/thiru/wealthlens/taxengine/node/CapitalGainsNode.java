package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Income under "Capital gains", part by part, after the head's own losses (S2 s.108(2); 1961 s.70(2), (3); D-243).
 *
 * <p>Each bucket of {@link CapitalGainsInput} belongs to the {@link CapitalGainsPart} the row of {@code RS-CG-PART} its
 * facts meet names (D-289): which gains a special rate charges is the rule data's, and a bucket no row covers is refused
 * rather than charged with total income. A
 * part whose buckets net to a loss contributes that loss: a long-term loss is set off only against the long-term
 * parts, a short-term loss against any part. The long-term loss goes first, because it can use fewer parts; each loss
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
        return Set.of(ROW, PARTS);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        GoverningRow row = GoverningRow.of(ctx, ROW);
        CapitalGainsAbsorptionOrder order = RuleDefinitions.require(row.version().definition(),
                CapitalGainsAbsorptionOrder.class, row.id());
        Map<CapitalGainsPart, Money> net = new EnumMap<>(CapitalGainsPart.class);
        for (CapitalGainsInput.Bucket bucket : ctx.inputs().capitalGains().buckets()) {
            net.merge(partOf(ctx, bucket), bucket.gain(), Money::plus);
        }
        List<TraceLine> lines = new ArrayList<>();
        Map<CapitalGainsPart, Money> available = new EnumMap<>(CapitalGainsPart.class);
        Money longTermLoss = Money.ZERO;
        Money shortTermLoss = Money.ZERO;
        for (CapitalGainsPart part : order.parts()) {
            Money amount = net.getOrDefault(part, Money.ZERO);
            if (amount.isLessThan(Money.ZERO)) {
                Money loss = Money.ZERO.minus(amount);
                lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.MEASURE, amount, Money.ZERO)
                        .withLimitedBy(part + ": a loss"));
                longTermLoss = part.isLongTerm() ? longTermLoss.plus(loss) : longTermLoss;
                shortTermLoss = part.isLongTerm() ? shortTermLoss : shortTermLoss.plus(loss);
            } else if (amount.isGreaterThan(Money.ZERO)) {
                lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.INCOME, amount, amount)
                        .withLimitedBy(part.toString()));
                available.put(part, amount);
            }
        }
        CapitalGainsAbsorption parts = new CapitalGainsAbsorption(order, available);
        Money longTermLeft = setOff(ctx, parts, longTermLoss, true, lines);
        Money shortTermLeft = setOff(ctx, parts, shortTermLoss, false, lines);
        CapitalGainsHead head = new CapitalGainsHead(available, shortTermLeft, longTermLeft);
        return new NodeResult(head, lines);
    }

    /**
     * The parts absorb up to {@code loss}, in order — only the long-term parts for a long-term loss (S2 s.108(2)(b)).
     * Returns what is left, which is shown.
     */
    private static Money setOff(NodeContext ctx, CapitalGainsAbsorption parts, Money loss, boolean longTerm,
            List<TraceLine> lines) {
        RuleId row = ctx.ruleIdOf(ROW);
        String which = longTerm ? "long-term" : "short-term";
        Predicate<CapitalGainsPart> reaches = longTerm ? CapitalGainsPart::isLongTerm : part -> true;
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
    private static CapitalGainsPart partOf(NodeContext ctx, CapitalGainsInput.Bucket bucket) {
        return ctx.optionalRuleOf(PARTS, factsOf(bucket))
                .map(row -> RuleDefinitions.require(TemporallyApportionedCap.governingVersion(row,
                        ctx.inputs().temporal()).definition(), BucketPart.class, row.id()).part())
                .orElseThrow(() -> new IllegalArgumentException("Capital gains of " + bucket.gain() + " ("
                        + bucket.term() + ", " + bucket.facts() + ") fall in no row of " + PARTS + " for "
                        + ctx.pack().taxYear().label() + ", so no provision is known to charge them"));
    }

    /**
     * The bucket's facts as the rows test them, its acquisition date among them; the corporate action only where the lot
     * came from one.
     */
    private static ItemFacts factsOf(CapitalGainsInput.Bucket bucket) {
        BucketFacts facts = bucket.facts();
        Map<ItemFactId, String> codes = new HashMap<>();
        codes.put(TERM, bucket.term().name());
        codes.put(ASSET, facts.assetKind());
        codes.put(STT_ON_ACQUISITION, sttCode(facts.sttOnAcquisition()));
        codes.put(STT_ON_TRANSFER, sttCode(facts.sttOnTransfer()));
        codes.put(ACQUISITION, facts.acquisitionMode());
        facts.corporateAction().ifPresent(action -> codes.put(CORPORATE_ACTION, action));
        return new ItemFacts(codes, Map.of(), Map.of(ACQUIRED_ON, facts.acquiredOn()));
    }

    private static String sttCode(boolean paid) {
        return paid ? "PAID" : "NOT_PAID";
    }
}
