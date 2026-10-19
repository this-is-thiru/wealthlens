package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CarriedHousePropertyLoss;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.RegimeVerdict;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.AbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsAbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.LossCarryForward;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.TaxYearSpan;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Income under the heads after a head's loss is set off against the others in the year (S2 s.109; 1961 s.71;
 * {@code DR 01} R18; D-217).
 *
 * <p>Each head's loss, taken in the order the assessee elected or else the declared {@link AbsorptionOrder}, is
 * absorbed by the other heads in that order —
 * a house-property loss only up to the {@code SETOFF-HOUSE-PROPERTY} limit, and not at all where the regime forbids it
 * (S2 s.202(2)(b)(ii); 1961 s.115BAC(2)(ii)(b)). Every set-off is a line naming the order, which neither Act
 * prescribes and the assessee may choose (D-221; {@code SetOffOrderElection} prices it). What is not set off is a MEASURE line: a house-property loss carried forward (W-081b) or, in the
 * new regime, deemed given full effect to and gone (S2 s.202(3)); a loss under other sources, which no provision
 * carries. Capital gains absorb a loss part by part, in {@code SETOFF-WITHIN-CAPITAL-GAINS}'s order (S2 s.109(1); 1961
 * s.71(2); D-243), and lend none: their own loss stays in their head ({@link CapitalGainsNode}). The value is the heads'
 * income after set-off — what gross total income adds up — with each capital-gains part, which is taxed on its own
 * basis.
 *
 * <p>Then the house-property loss carried in from earlier years ({@link LedgerSnapshot}) is set off against what
 * house-property income is left, oldest year first, the part attributable to a deduction the new regime disallows
 * before the rest — after this year's set-off, as the return computes it (ITR-2 Schedule BFLA), and otherwise a declared
 * default, the Acts prescribing none (D-221). A year's loss is used for eight years after it arose
 * (S2 s.110; 1961 s.71B), and expires after that; in the new regime its attributable part is deemed given full effect
 * to (S2 s.202(2)(b)(i), (3)). What is still carried is a MEASURE line per year of origin (D-218).
 *
 * <p>Last, the capital losses carried in from earlier years, term by term ({@link CarriedCapitalLossSetOff}; S2 s.111;
 * 1961 s.74; D-247).
 */
public final class SetOffNode implements ComputationNode {

    private static final BaseId LOSS = new BaseId("LOSS");

    /**
     * The order the heads absorb a loss in this run, and whether the assessee elected it — said on every line — with
     * the order the capital-gains parts absorb it in.
     */
    private record OrderInUse(AbsorptionOrder order, String basis, CapitalGainsAbsorptionOrder capitalGainsOrder) {
    }

    @Override
    public NodeId produces() {
        return NodeIds.SETOFF;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SALARY_INCOME, NodeIds.HOUSEPROPERTY_INCOME, NodeIds.OTHERSOURCES_INCOME,
                NodeIds.CAPITALGAINS_INCOME);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_SETOFF_ACROSS_HEADS, RuleSetIds.RS_SETOFF_HOUSE_PROPERTY, RuleSetIds.RS_SETOFF_CARRIED_HOUSE_PROPERTY,
                RuleSetIds.RS_SETOFF_CARRIED_DISALLOWED_PART, RuleSetIds.RS_SETOFF_WITHIN_CAPITAL_GAINS,
                RuleSetIds.RS_SETOFF_CARRIED_CAPITAL_GAINS);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        TemporalContext t = ctx.inputs().temporal();
        Map<HeadCode, Money> heads = new EnumMap<>(HeadCode.class);
        heads.put(HeadCode.SALARIES, ctx.get(NodeIds.SALARY_INCOME, MoneyValue.class).asMoney());
        heads.put(HeadCode.HOUSE_PROPERTY, ctx.get(NodeIds.HOUSEPROPERTY_INCOME, MoneyValue.class).asMoney());
        heads.put(HeadCode.OTHER_SOURCES, ctx.get(NodeIds.OTHERSOURCES_INCOME, MoneyValue.class).asMoney());
        CapitalGainsHead capitalGains = ctx.get(NodeIds.CAPITALGAINS_INCOME, CapitalGainsHead.class);
        Map<CapitalGainsPart, Money> parts = new EnumMap<>(capitalGains.parts());
        heads.put(HeadCode.CAPITAL_GAINS, capitalGains.total());
        AbsorptionOrder declared = RuleDefinitions.require(version(ctx, t, ctx.ruleIdOf(RuleSetIds.RS_SETOFF_ACROSS_HEADS)).definition(),
                AbsorptionOrder.class, ctx.ruleIdOf(RuleSetIds.RS_SETOFF_ACROSS_HEADS));
        Optional<List<HeadCode>> elected = ctx.inputs().entered().absorptionOrder();
        elected.ifPresent(stated -> requireSameHeads(stated, declared));
        OrderInUse order = new OrderInUse(elected.map(AbsorptionOrder::new).orElse(declared), elected.isPresent()
                ? "elected (S2 s.109(1); 1961 s.71; D-221)"
                : "the declared default: the Act prescribes none and none was elected (D-221)",
                RuleDefinitions.require(version(ctx, t, ctx.ruleIdOf(RuleSetIds.RS_SETOFF_WITHIN_CAPITAL_GAINS)).definition(),
                        CapitalGainsAbsorptionOrder.class, ctx.ruleIdOf(RuleSetIds.RS_SETOFF_WITHIN_CAPITAL_GAINS)));
        Map<HeadCode, Money> income = new EnumMap<>(HeadCode.class);
        heads.forEach((head, amount) -> income.put(head, Money.max(amount, Money.ZERO)));
        List<TraceLine> lines = new ArrayList<>();
        for (HeadCode lossHead : order.order().heads()) {
            Money loss = Money.ZERO.minus(heads.getOrDefault(lossHead, Money.ZERO));
            if (loss.isGreaterThan(Money.ZERO)) {
                setOff(ctx, t, order, lossHead, loss, new Absorbing(income, parts), lines);
            }
        }
        broughtForward(ctx, t, income, lines);
        CarriedCapitalLossSetOff.apply(ctx, order.capitalGainsOrder(), income, parts, lines);
        Money total = income.values().stream().reduce(Money.ZERO, Money::plus);
        return new NodeResult(new HeadsAfterSetOff(total, parts), lines);
    }

    /** One head's loss: what may be set off, set off against the others in order, and what is left. */
    private static void setOff(NodeContext ctx, TemporalContext t, OrderInUse order, HeadCode lossHead,
            Money loss, Absorbing into, List<TraceLine> lines) {
        boolean houseProperty = lossHead == HeadCode.HOUSE_PROPERTY;
        RuleId row = houseProperty ? ctx.ruleIdOf(RuleSetIds.RS_SETOFF_HOUSE_PROPERTY) : ctx.ruleIdOf(RuleSetIds.RS_SETOFF_ACROSS_HEADS);
        RuleVersion version = version(ctx, t, row);
        RegimeVerdict verdict = ctx.inRegime(row, version);
        if (!verdict.applies()) {
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.SET_OFF, loss, Money.ZERO)
                    .withLimitedBy(verdict.whyNot()));
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.MEASURE, loss, loss)
                    .withLimitedBy("deemed to have been given"
                    + " full effect to, and not carried forward (S2 s.202(3); 1961 s.115BAC(3)): " + verdict.whyNot()));
            return;
        }
        Money allowed = loss;
        if (houseProperty) {
            CapResult limit = TemporallyApportionedCap.apply(ctx.rule(row), t, CapContext.of(LOSS, loss));
            allowed = limit.value();
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.MEASURE, loss, allowed)
                    .withLimitedBy(limit.limitedBy()));
        }
        Money setOff = absorb(ctx, order, lossHead, allowed, into, lines, row);
        Money left = loss.minus(setOff);
        if (left.isGreaterThan(Money.ZERO)) {
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.MEASURE, loss, left).withLimitedBy(houseProperty
                    ? "not set off: carried forward against income from house property (S2 s.110; 1961 s.71B) — W-081b"
                    : "not set off, and not carried forward: S2 ss.110–115 carry no loss under other sources"));
        }
    }

    /** The heads' income and the capital-gains parts, both reduced as a loss is absorbed. */
    private record Absorbing(Map<HeadCode, Money> income, Map<CapitalGainsPart, Money> parts) {
    }

    /** The other heads absorb up to {@code allowed} of the loss, in order; returns how much they took. */
    private static Money absorb(NodeContext ctx, OrderInUse order, HeadCode lossHead, Money allowed, Absorbing into,
            List<TraceLine> lines, RuleId row) {
        Money remaining = allowed;
        for (HeadCode head : order.order().heads()) {
            Money available = into.income().getOrDefault(head, Money.ZERO);
            if (head == lossHead || !remaining.isGreaterThan(Money.ZERO) || !available.isGreaterThan(Money.ZERO)) {
                continue;
            }
            Money taken = head == HeadCode.CAPITAL_GAINS
                    ? absorbCapitalGains(ctx, order, lossHead, remaining, into.parts(), lines, row)
                    : Money.min(remaining, available);
            into.income().put(head, available.minus(taken));
            remaining = remaining.minus(taken);
            if (head != HeadCode.CAPITAL_GAINS) {
                lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.SET_OFF, available, taken)
                        .withLimitedBy(lossHead
                        + " loss against " + head + "; order " + order.order().heads() + ", " + order.basis()));
            }
        }
        return allowed.minus(remaining);
    }

    /** The capital-gains parts absorb up to {@code loss}, in their order, a line each; returns how much they took. */
    private static Money absorbCapitalGains(NodeContext ctx, OrderInUse order, HeadCode lossHead, Money loss,
            Map<CapitalGainsPart, Money> parts, List<TraceLine> lines, RuleId row) {
        Money remaining = loss;
        for (CapitalGainsPart part : order.capitalGainsOrder().parts()) {
            Money available = parts.getOrDefault(part, Money.ZERO);
            if (!remaining.isGreaterThan(Money.ZERO) || !available.isGreaterThan(Money.ZERO)) {
                continue;
            }
            Money taken = Money.min(remaining, available);
            parts.put(part, available.minus(taken));
            remaining = remaining.minus(taken);
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.SET_OFF, available, taken).withLimitedBy(lossHead
                    + " loss against CAPITAL_GAINS " + part + "; order " + order.order().heads() + ", " + order.basis()
                    + "; within capital gains " + order.capitalGainsOrder().parts() + ", the declared default (OQ-32)"));
        }
        return loss.minus(remaining);
    }

    /** The carried house-property losses, oldest first, against the house-property income left (S2 s.110; 1961 s.71B). */
    private static void broughtForward(NodeContext ctx, TemporalContext t, Map<HeadCode, Money> income,
            List<TraceLine> lines) {
        RuleId carriedRow = ctx.ruleIdOf(RuleSetIds.RS_SETOFF_CARRIED_HOUSE_PROPERTY);
        LossCarryForward carry = RuleDefinitions.require(version(ctx, t, carriedRow).definition(),
                LossCarryForward.class, carriedRow);
        requireAgainstAHead(carry, carriedRow);
        TaxYearSpan span = carry.span();
        TaxYearId thisYear = ctx.inputs().taxYear();
        Map<TaxYearId, CarriedHousePropertyLoss> byYear = new TreeMap<>(Comparator.comparingInt(TaxYearId::startYear));
        byYear.putAll(ctx.inputs().ledgers().housePropertyLossCarriedForward());
        for (Map.Entry<TaxYearId, CarriedHousePropertyLoss> entry : byYear.entrySet()) {
            TaxYearId arose = entry.getKey();
            if (arose.startYear() >= thisYear.startYear()) {
                throw new IllegalArgumentException("A house-property loss carried forward from " + arose.label()
                        + " cannot be brought into " + thisYear.label() + "; a loss is carried only to later years");
            }
            CarriedHousePropertyLoss carried = entry.getValue();
            Money remaining = carried.loss().minus(carried.absorbedSince());
            if (!remaining.isGreaterThan(Money.ZERO)) {
                continue;
            }
            if (!span.holds(arose, thisYear)) {
                lines.add(ctx.traceLine(lines.size() + 1, ctx.ruleIdOf(RuleSetIds.RS_SETOFF_CARRIED_HOUSE_PROPERTY),
                                StepKind.MEASURE, remaining, remaining)
                        .withLimitedBy("loss of " + arose.label() + " expired: carried for no more than "
                                + span.succeedingYears() + " tax years after it arose (S2 s.110(2); 1961 s.71B)"));
                continue;
            }
            Money attributable = Money.max(carried.attributableToNewRegimeDisallowance().minus(carried.absorbedSince()),
                    Money.ZERO);
            Money left = useAttributable(ctx, carry.absorbedBy(), arose, attributable, income, lines)
                    .plus(use(ctx, carry.absorbedBy(), carriedRow, arose, remaining.minus(attributable), income,
                            lines));
            if (left.isGreaterThan(Money.ZERO)) {
                lines.add(ctx.traceLine(lines.size() + 1, ctx.ruleIdOf(RuleSetIds.RS_SETOFF_CARRIED_HOUSE_PROPERTY),
                                StepKind.MEASURE, remaining, left)
                        .withLimitedBy("loss of " + arose.label() + " still carried forward (S2 s.110; 1961 s.71B)"));
            }
        }
    }

    /** The attributable part: used like the rest, or, where the regime forbids it, gone. Returns what is still carried. */
    private static Money useAttributable(NodeContext ctx, HeadCode absorbedBy, TaxYearId arose, Money attributable,
            Map<HeadCode, Money> income, List<TraceLine> lines) {
        if (!attributable.isGreaterThan(Money.ZERO)) {
            return Money.ZERO;
        }
        RuleId row = ctx.ruleIdOf(RuleSetIds.RS_SETOFF_CARRIED_DISALLOWED_PART);
        RuleVersion version = version(ctx, ctx.inputs().temporal(), row);
        RegimeVerdict verdict = ctx.inRegime(row, version);
        if (!verdict.applies()) {
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.SET_OFF, attributable, Money.ZERO)
                    .withLimitedBy("loss of " + arose.label() + ": " + verdict.whyNot()));
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.MEASURE, attributable, attributable)
                    .withLimitedBy("loss of "
                    + arose.label() + " deemed to have been given full effect to, and not carried forward (S2 s.202(3);"
                    + " 1961 s.115BAC(3))"));
            return Money.ZERO;
        }
        return use(ctx, absorbedBy, row, arose, attributable, income, lines);
    }

    /** Sets off up to {@code amount} of one year's loss against the income left under the row's head; returns the rest.
     */
    private static Money use(NodeContext ctx, HeadCode absorbedBy, RuleId row, TaxYearId arose, Money amount,
            Map<HeadCode, Money> income, List<TraceLine> lines) {
        Money available = income.getOrDefault(absorbedBy, Money.ZERO);
        Money taken = Money.min(amount, available);
        if (taken.isGreaterThan(Money.ZERO)) {
            income.put(absorbedBy, available.minus(taken));
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.SET_OFF, available, taken).withLimitedBy("loss of "
                    + arose.label() + " against " + absorbedBy
                    + "; order: this year's set-off first (ITR-2 Schedule BFLA), then the oldest year, the attributable"
                    + " part first — the declared default, the Acts prescribing"
                    + " none (D-221)"));
        }
        return amount.minus(taken);
    }

    /**
     * Refuses a row this path cannot follow: it lowers one head's income, not the capital-gains parts, and the
     * house-property ledger states no return fact for a return condition to test (S2 s.121 and 1961 s.80 do not reach
     * s.110 / s.71B; D-263).
     */
    private static void requireAgainstAHead(LossCarryForward carry, RuleId row) {
        if (carry.absorbedBy() == HeadCode.CAPITAL_GAINS) {
            throw new IllegalArgumentException(row + " carries a loss against CAPITAL_GAINS, but the house-property loss"
                    + " is set off against one head's income, not the capital-gains parts; the row is a pack defect"
                    + " (D-263)");
        }
        if (carry.requiresReturnFiledInTime()) {
            throw new IllegalArgumentException(row + " requires a return filed in time, but the house-property ledger"
                    + " states no such fact to test; the row is a pack defect (D-263)");
        }
    }

    /** Refuses an elected order that leaves out a head or adds one: every head's loss is taken in the order. */
    private static void requireSameHeads(List<HeadCode> elected, AbsorptionOrder declared) {
        if (!Set.copyOf(elected).equals(Set.copyOf(declared.heads()))) {
            throw new IllegalArgumentException("The elected absorption order " + elected + " must name the heads "
                    + declared.heads() + ", each once, in the order the assessee chooses");
        }
    }

    private static RuleVersion version(NodeContext ctx, TemporalContext t, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), t);
    }

}
