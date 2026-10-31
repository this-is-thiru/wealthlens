package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.IndexedGain;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.BucketPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsAbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.NotModelledProvision;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.Notice;
import com.thiru.wealthlens.taxengine.trace.Severity;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceAnnotation;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Income under "Capital gains", part by part, after the head's own losses (S2 s.108(2); 1961 s.70(2), (3); D-243).
 *
 * <p>Each bucket's gain is its consideration less its cost less the charges {@link CapitalGainsCharges}' rows deduct
 * (2025 s.72; 1961 s.48; W-162a), and so much of a long-term gain as the taxpayer put into a residential house is
 * taken out of it before the part is chosen ({@link ReinvestmentExemption}; 2025 s.86; 1961 s.54F; W-088i). Each bucket of {@link CapitalGainsInput} belongs to the {@link CapitalGainsPart} the row of {@code RS-CG-PART} its
 * facts meet names (D-289), its STT read through {@link CapitalGainsStt} (D-298): which gains a special rate charges is the rule data's, and a bucket no row covers is refused
 * rather than charged with total income; whether the head takes a bucket at all is {@link CapitalGainsScope}'s rows'
 * (W-160a). A
 * part whose buckets net to a loss contributes that loss, of the term {@code CG-PARTS} declares for the part: a
 * long-term loss is set off only against the parts that row lets it reach, a short-term loss against any part (D-309). The long-term loss goes first, because it can use fewer parts; each loss
 * takes the parts in {@code SETOFF-WITHIN-CAPITAL-GAINS}'s declared order, which neither Act prescribes (OQ-32), and
 * every set-off line names it. What no part absorbs is shown and stays in the head — a capital-gains loss is not set
 * off against another head (S2 s.109(2); 1961 s.71(3)); it is carried forward (S2 s.111; 1961 s.74; D-247).
 *
 * <p>Each part's gain is carried twice: as it is, and with the cost of the buckets {@code RS-CG-INDEXED-COST} qualifies
 * lifted by the Cost Inflation Index (W-088g, D-426). The second measure is what {@code RATE-CG-LONG-OTHER}'s cap
 * compares a twenty per cent rate against; a bucket that does not qualify carries its gain as both, which leaves the
 * twelve and one-half per cent limb the lesser for it.
 */
public final class CapitalGainsNode implements ComputationNode {

    /** The provisions not modelled yet that a bucket's notice names (W-088g4). */
    private static final RuleSetId NOT_MODELLED = RuleSetIds.RS_CG_NOT_MODELLED;

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
                CapitalGainsCharges.DEDUCTIONS, CapitalGainsIndexation.QUALIFIER,
                RuleSetIds.RS_RATE_CG_COST_INFLATION_INDEX, ReinvestmentExemption.EXEMPTION, NOT_MODELLED);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        GoverningRow row = GoverningRow.of(ctx, ROW);
        CapitalGainsAbsorptionOrder order = RuleDefinitions.require(row.version().definition(),
                CapitalGainsAbsorptionOrder.class, row.id());
        Map<CapitalGainsPart, Money> net = new HashMap<>();
        Map<CapitalGainsPart, Money> gaining = new HashMap<>();
        Map<CapitalGainsPart, List<IndexedGain>> qualifying = new HashMap<>();
        List<TraceLine> lines = new ArrayList<>();
        List<TraceAnnotation> annotations = new ArrayList<>();
        List<ReinvestmentExemption.BucketGain> taken = takenBuckets(ctx, lines, annotations);
        List<Money> chargeableGains = ReinvestmentExemption.chargeableGainsOf(ctx, taken, lines);
        for (int position = 0; position < taken.size(); position++) {
            CapitalGainsInput.Bucket bucket = taken.get(position).bucket();
            Term term = taken.get(position).term();
            Money chargeable = chargeableGains.get(position);
            CapitalGainsPart part = partOf(ctx, bucket, chargeable, term,
                    CapitalGainsStt.of(ctx, bucket, chargeable, term, lines));
            net.merge(part, chargeable, Money::plus);
            Optional<Money> indexed = CapitalGainsIndexation.indexedGainOf(ctx, bucket, chargeable, lines);
            if (chargeable.isGreaterThan(Money.ZERO)) {
                gaining.merge(part, chargeable, Money::plus);
                indexed.ifPresent(measure -> qualifying.computeIfAbsent(part, held -> new ArrayList<>())
                        .add(new IndexedGain(chargeable, measureOf(ctx, row.id(), part, measure, lines))));
            }
        }
        DeclaredCapitalGainsParts declared = DeclaredCapitalGainsParts.of(ctx);
        Map<CapitalGainsPart, Money> available = new HashMap<>();
        Map<CapitalGainsPart, List<IndexedGain>> indexed = new HashMap<>();
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
                if (qualifying.containsKey(part)) {
                    indexed.put(part, IndexedGain.lowered(qualifying.get(part), gaining.get(part), amount));
                }
            }
        }
        CapitalGainsAbsorption parts = new CapitalGainsAbsorption(order, available, indexed);
        Money longTermLeft = setOff(ctx, parts, longTermLoss, true, declared, lines);
        Money shortTermLeft = setOff(ctx, parts, shortTermLoss, false, declared, lines);
        CapitalGainsHead head = new CapitalGainsHead(available, indexed, shortTermLeft, longTermLeft);
        return new NodeResult(head, lines, annotations);
    }

    /** A qualifying gain's indexed measure, nil rather than negative — a measure a cost that rose has already emptied. */
    private static Money measureOf(NodeContext ctx, RuleId row, CapitalGainsPart part, Money measure,
            List<TraceLine> lines) {
        if (!measure.isLessThan(Money.ZERO)) {
            return measure;
        }
        lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.MEASURE, measure, Money.ZERO)
                .withLimitedBy(part + ": indexed cost above the consideration, so nothing is left at the 20 per cent"
                        + " measure"));
        return Money.ZERO;
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
    /**
     * Each bucket the head takes, with its gain and its term — all of them before any is exempted, because a house claim
     * is measured over every bucket of its transfer together ({@link ReinvestmentExemption}; W-088i5) — and on each
     * bucket's term line, a notice for every provision not modelled that reaches it.
     */
    private static List<ReinvestmentExemption.BucketGain> takenBuckets(NodeContext ctx, List<TraceLine> lines,
            List<TraceAnnotation> annotations) {
        List<ReinvestmentExemption.BucketGain> taken = new ArrayList<>();
        for (CapitalGainsInput.Bucket bucket : ctx.inputs().capitalGains().buckets()) {
            CapitalGainsCharges.Gain gain = CapitalGainsCharges.gainOf(ctx, bucket, lines);
            if (CapitalGainsScope.takes(ctx, bucket, gain.gain(), lines)) {
                taken.add(new ReinvestmentExemption.BucketGain(bucket, gain,
                        CapitalGainsTerm.of(ctx, bucket, gain.gain(), lines)));
                noticesOf(ctx, bucket).ifPresent(notices ->
                        annotations.add(new TraceAnnotation(lines.getLast().sequence(), notices)));
            }
        }
        return taken;
    }

    /**
     * The notices for the provisions {@code RS-CG-NOT-MODELLED}'s rows say reach the bucket (W-088g4, D-432): one that
     * could raise the tax makes the figure provisional ({@link Severity#ACTION_REQUIRED}); one that could only lower it
     * leaves the figure standing as the most it can be ({@link Severity#ATTENTION}). None where no row reaches it.
     */
    private static Optional<List<Notice>> noticesOf(NodeContext ctx, CapitalGainsInput.Bucket bucket) {
        ItemFacts facts = new ItemFacts(Map.of(ASSET, bucket.facts().assetKind()), Map.of(),
                Map.of(ACQUIRED_ON, bucket.facts().acquiredOn()));
        List<Notice> notices = new ArrayList<>();
        for (RuleRow row : ctx.rulesOf(NOT_MODELLED)) {
            if (RowReach.holdsFor(ctx, row, facts)) {
                RuleVersion version = TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal());
                NotModelledProvision provision = RuleDefinitions.require(version.definition(),
                        NotModelledProvision.class, row.id());
                boolean mayRaise = provision.effect() == NotModelledProvision.TaxEffect.MAY_RAISE;
                notices.add(Notice.about(mayRaise ? Severity.ACTION_REQUIRED : Severity.ATTENTION,
                        "Not applied yet: " + provision.subject() + " (2025 " + version.provision2025().reference()
                                + "; 1961 " + version.provision1961().reference() + "). "
                                + (mayRaise ? "Applying it can raise the tax on this sale, so the figure is provisional"
                                        + " until it is checked."
                                        : "Applying it can only lower the tax on this sale, so the figure is the most it"
                                                + " can be."),
                        row.id()));
            }
        }
        return notices.isEmpty() ? Optional.empty() : Optional.of(notices);
    }

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
