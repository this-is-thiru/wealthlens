package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.input.RateBasisHint;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsAbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Income under "Capital gains", part by part, after the head's own losses (S2 s.108(2); 1961 s.70(2), (3); D-243).
 *
 * <p>Each bucket of {@link CapitalGainsInput} belongs to one {@link CapitalGainsPart} by its term and rate basis. A
 * part whose buckets net to a loss contributes that loss: a long-term loss is set off only against the long-term
 * parts, a short-term loss against any part. The long-term loss goes first, because it can use fewer parts; each loss
 * takes the parts in {@code SETOFF-WITHIN-CAPITAL-GAINS}'s declared order, which neither Act prescribes (OQ-32), and
 * every set-off line names it. What no part absorbs is shown and stays in the head — a capital-gains loss is not set
 * off against another head (S2 s.109(2); 1961 s.71(3)), and carrying it is W-088d's.
 */
public final class CapitalGainsNode implements ComputationNode {

    private static final RuleId ROW = RuleIds.SETOFF_WITHIN_CAPITAL_GAINS;

    @Override
    public NodeId produces() {
        return NodeIds.CAPITALGAINS_INCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(ROW);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(ROW), ctx.inputs().temporal());
        CapitalGainsAbsorptionOrder order = RuleDefinitions.require(version.definition(),
                CapitalGainsAbsorptionOrder.class, ROW);
        Map<CapitalGainsPart, Money> net = new EnumMap<>(CapitalGainsPart.class);
        for (CapitalGainsInput.Bucket bucket : ctx.inputs().capitalGains().buckets()) {
            net.merge(partOf(bucket), bucket.gain(), Money::plus);
        }
        List<TraceLine> lines = new ArrayList<>();
        Map<CapitalGainsPart, Money> available = new EnumMap<>(CapitalGainsPart.class);
        Money longTermLoss = Money.ZERO;
        Money shortTermLoss = Money.ZERO;
        for (CapitalGainsPart part : order.parts()) {
            Money amount = net.getOrDefault(part, Money.ZERO);
            if (amount.isLessThan(Money.ZERO)) {
                Money loss = Money.ZERO.minus(amount);
                lines.add(line(lines, version, StepKind.MEASURE, amount, Money.ZERO).withLimitedBy(part + ": a loss"));
                longTermLoss = part.isLongTerm() ? longTermLoss.plus(loss) : longTermLoss;
                shortTermLoss = part.isLongTerm() ? shortTermLoss : shortTermLoss.plus(loss);
            } else if (amount.isGreaterThan(Money.ZERO)) {
                lines.add(line(lines, version, StepKind.INCOME, amount, amount).withLimitedBy(part.toString()));
                available.put(part, amount);
            }
        }
        Money longTermLeft = setOff(longTermLoss, true, order, available, lines, version);
        Money shortTermLeft = setOff(shortTermLoss, false, order, available, lines, version);
        CapitalGainsHead head = new CapitalGainsHead(available, shortTermLeft, longTermLeft);
        return new NodeResult(head, lines);
    }

    /**
     * The parts absorb up to {@code loss}, in order — only the long-term parts for a long-term loss (S2 s.108(2)(b)).
     * Returns what is left, which is shown.
     */
    private static Money setOff(Money loss, boolean longTerm, CapitalGainsAbsorptionOrder order,
            Map<CapitalGainsPart, Money> available, List<TraceLine> lines, RuleVersion version) {
        Money remaining = loss;
        String which = longTerm ? "long-term" : "short-term";
        for (CapitalGainsPart part : order.parts()) {
            Money gain = available.getOrDefault(part, Money.ZERO);
            if ((longTerm && !part.isLongTerm()) || !remaining.isGreaterThan(Money.ZERO)
                    || !gain.isGreaterThan(Money.ZERO)) {
                continue;
            }
            Money taken = Money.min(remaining, gain);
            available.put(part, gain.minus(taken));
            remaining = remaining.minus(taken);
            lines.add(line(lines, version, StepKind.SET_OFF, gain, taken).withLimitedBy(which + " loss against " + part
                    + "; order " + order.parts() + ", the declared default: neither Act prescribes one (OQ-32)"));
        }
        if (remaining.isGreaterThan(Money.ZERO)) {
            lines.add(line(lines, version, StepKind.MEASURE, loss, remaining).withLimitedBy(which + " loss not set off,"
                    + " and not against another head (S2 s.109(2); 1961 s.71(3)); carried forward — W-088d"));
        }
        return remaining;
    }

    /** The part a bucket's term and basis make (D-243); the two pairings no provision makes are refused. */
    private static CapitalGainsPart partOf(CapitalGainsInput.Bucket bucket) {
        Term term = bucket.term();
        RateBasisHint basis = bucket.basis();
        return switch (basis) {
            case SPECIAL_RATE_LISTED_EQUITY -> term == Term.SHORT_TERM ? CapitalGainsPart.SHORT_TERM_LISTED_EQUITY
                    : CapitalGainsPart.LONG_TERM_LISTED_EQUITY;
            case SLAB -> requireTerm(term, Term.SHORT_TERM, basis, CapitalGainsPart.SHORT_TERM_SLAB);
            case SPECIAL_RATE_OTHER -> requireTerm(term, Term.LONG_TERM, basis, CapitalGainsPart.LONG_TERM_OTHER);
        };
    }

    private static CapitalGainsPart requireTerm(Term term, Term expected, RateBasisHint basis, CapitalGainsPart part) {
        if (term != expected) {
            throw new IllegalArgumentException("A " + term + " capital gain on the " + basis + " basis falls under no"
                    + " provision: only " + expected + " gains are charged on it (S2 ss.196–198)");
        }
        return part;
    }

    private static TraceLine line(List<TraceLine> lines, RuleVersion version, StepKind kind, Money in, Money out) {
        return TraceLine.of(lines.size() + 1, NodeIds.CAPITALGAINS_INCOME, ROW, kind, MoneyValue.of(in),
                MoneyValue.of(out), version.provenance());
    }
}
