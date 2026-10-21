package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.node.CarriedLossSetOff.CarriedLoss;
import com.thiru.wealthlens.taxengine.node.CarriedLossSetOff.CarriedLossTexts;
import com.thiru.wealthlens.taxengine.node.CarriedLossSetOff.CarriedLosses;
import com.thiru.wealthlens.taxengine.node.CarriedLossSetOff.Portion;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
 * <p>Then the losses carried in from earlier years ({@link LedgerSnapshot}), each ledger stated as portions and set off
 * by {@link CarriedLossSetOff} under its row's span, head and return condition (D-263). First the house-property loss,
 * against what house-property income is left, the part attributable to a deduction the new regime disallows before the
 * rest — after this year's set-off, as the return computes it (ITR-2 Schedule BFLA), and otherwise a declared default,
 * the Acts prescribing none (D-221); in the new regime that part is deemed given full effect to (S2 s.202(2)(b)(i),
 * (3)) (S2 s.110; 1961 s.71B; D-218). Last, the capital losses, term by term (S2 s.111; 1961 s.74; D-247).
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
                RuleSetIds.RS_SETOFF_CARRIED_CAPITAL_GAINS, DeclaredCapitalGainsParts.PARTS);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        TemporalContext t = ctx.inputs().temporal();
        Map<HeadCode, Money> heads = new EnumMap<>(HeadCode.class);
        heads.put(HeadCode.SALARIES, ctx.get(NodeIds.SALARY_INCOME, MoneyValue.class).asMoney());
        heads.put(HeadCode.HOUSE_PROPERTY, ctx.get(NodeIds.HOUSEPROPERTY_INCOME, MoneyValue.class).asMoney());
        heads.put(HeadCode.OTHER_SOURCES, ctx.get(NodeIds.OTHERSOURCES_INCOME, MoneyValue.class).asMoney());
        CapitalGainsHead capitalGains = ctx.get(NodeIds.CAPITALGAINS_INCOME, CapitalGainsHead.class);
        Map<CapitalGainsPart, Money> parts = new HashMap<>(capitalGains.parts());
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
        SetOffContext run = new SetOffContext(ctx, order, income,
                new CapitalGainsAbsorption(order.capitalGainsOrder(), parts), lines);
        for (HeadCode lossHead : order.order().heads()) {
            Money loss = Money.ZERO.minus(heads.getOrDefault(lossHead, Money.ZERO));
            if (loss.isGreaterThan(Money.ZERO)) {
                setOff(run, lossHead, loss);
            }
        }
        CarriedLossSetOff.apply(ctx, carriedHousePropertyLosses(ctx, t), income, run.parts(), lines);
        CarriedLossSetOff.apply(ctx, carriedCapitalLosses(ctx, t, order.capitalGainsOrder(),
                DeclaredCapitalGainsParts.of(ctx)), income, run.parts(), lines);
        Money total = income.values().stream().reduce(Money.ZERO, Money::plus);
        return new NodeResult(new HeadsAfterSetOff(total, parts), lines);
    }

    /** One head's loss: what may be set off, set off against the others in order, and what is left. */
    private static void setOff(SetOffContext run, HeadCode lossHead, Money loss) {
        NodeContext ctx = run.ctx();
        TemporalContext t = ctx.inputs().temporal();
        List<TraceLine> lines = run.lines();
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
        Money setOff = absorb(run, lossHead, allowed, row);
        Money left = loss.minus(setOff);
        if (left.isGreaterThan(Money.ZERO)) {
            lines.add(ctx.traceLine(lines.size() + 1, row, StepKind.MEASURE, loss, left).withLimitedBy(houseProperty
                    ? "not set off: carried forward against income from house property (S2 s.110; 1961 s.71B) — W-081b"
                    : "not set off, and not carried forward: S2 ss.110–115 carry no loss under other sources"));
        }
    }

    /**
     * What one run's set-offs share: the order in use, and the heads' income and capital-gains parts, both lowered in
     * place as each loss is absorbed, with the lines written so far (D-263).
     */
    private record SetOffContext(NodeContext ctx, OrderInUse order, Map<HeadCode, Money> income,
            CapitalGainsAbsorption parts, List<TraceLine> lines) {
    }

    /** The other heads absorb up to {@code allowed} of the loss, in order; returns how much they took. */
    private static Money absorb(SetOffContext run, HeadCode lossHead, Money allowed, RuleId row) {
        OrderInUse order = run.order();
        List<TraceLine> lines = run.lines();
        Money remaining = allowed;
        for (HeadCode head : order.order().heads()) {
            Money available = run.income().getOrDefault(head, Money.ZERO);
            if (head == lossHead || !remaining.isGreaterThan(Money.ZERO) || !available.isGreaterThan(Money.ZERO)) {
                continue;
            }
            Money taken = head == HeadCode.CAPITAL_GAINS
                    ? absorbCapitalGains(run, lossHead, remaining, row)
                    : Money.min(remaining, available);
            run.income().put(head, available.minus(taken));
            remaining = remaining.minus(taken);
            if (head != HeadCode.CAPITAL_GAINS) {
                lines.add(run.ctx().traceLine(lines.size() + 1, row, StepKind.SET_OFF, available, taken)
                        .withLimitedBy(lossHead
                        + " loss against " + head + "; order " + order.order().heads() + ", " + order.basis()));
            }
        }
        return allowed.minus(remaining);
    }

    /** The capital-gains parts absorb up to {@code loss}, in their order, a line each; returns how much they took. */
    private static Money absorbCapitalGains(SetOffContext run, HeadCode lossHead, Money loss, RuleId row) {
        OrderInUse order = run.order();
        return run.parts().absorb(loss, part -> true, run.lines(), (sequence, part, available, taken) -> run.ctx()
                .traceLine(sequence, row, StepKind.SET_OFF, available, taken).withLimitedBy(lossHead
                        + " loss against CAPITAL_GAINS " + part + "; order " + order.order().heads() + ", "
                        + order.basis() + "; within capital gains " + order.capitalGainsOrder().parts()
                        + ", the declared default (OQ-32)"));
    }

    /**
     * The house-property ledger's losses: the part attributable to a deduction the new regime disallows, under its own
     * row, before the rest (S2 s.110; 1961 s.71B). The ledger states no return fact, neither Act asking one (D-263).
     */
    private static CarriedLosses carriedHousePropertyLosses(NodeContext ctx, TemporalContext t) {
        RuleId row = ctx.ruleIdOf(RuleSetIds.RS_SETOFF_CARRIED_HOUSE_PROPERTY);
        LossCarryForward carry = RuleDefinitions.require(version(ctx, t, row).definition(), LossCarryForward.class, row);
        if (carry.absorbedBy() == HeadCode.CAPITAL_GAINS) {
            throw new IllegalArgumentException(row + " carries a loss against CAPITAL_GAINS, but the house-property loss"
                    + " is set off against one head's income, not the capital-gains parts; the row is a pack defect"
                    + " (D-263)");
        }
        RuleId attributableRow = ctx.ruleIdOf(RuleSetIds.RS_SETOFF_CARRIED_DISALLOWED_PART);
        List<CarriedLoss> years = new ArrayList<>();
        ctx.inputs().ledgers().housePropertyLossCarriedForward().forEach((arose, carried) -> {
            Money remaining = carried.loss().minus(carried.absorbedSince());
            Money attributable = Money.max(carried.attributableToNewRegimeDisallowance().minus(carried.absorbedSince()),
                    Money.ZERO);
            Function<String, String> text = absorber -> "loss of " + arose.label() + " against " + absorber
                    + "; order: this year's set-off first (ITR-2 Schedule BFLA), then the oldest year, the attributable"
                    + " part first — the declared default, the Acts prescribing none (D-221)";
            years.add(new CarriedLoss(arose, Optional.empty(), List.of(
                    new Portion(attributable, attributableRow, part -> true, text),
                    new Portion(remaining.minus(attributable), row, part -> true, text))));
        });
        return new CarriedLosses(row, carry, new CarriedLossTexts("house-property loss", "S2 s.110(2); 1961 s.71B",
                "S2 s.110; 1961 s.71B", Optional.empty()), years);
    }

    /**
     * The capital-loss ledger's losses: the long-term loss, which only the parts {@code declared} lets it reach may
     * take, before the short-term, each against the parts in {@code order} (S2 s.111, s.121, s.536(2)(n); 1961 s.74,
     * s.80; D-247, D-309).
     */
    private static CarriedLosses carriedCapitalLosses(NodeContext ctx, TemporalContext t,
            CapitalGainsAbsorptionOrder order, DeclaredCapitalGainsParts declared) {
        RuleId row = ctx.ruleIdOf(RuleSetIds.RS_SETOFF_CARRIED_CAPITAL_GAINS);
        LossCarryForward carry = RuleDefinitions.require(version(ctx, t, row).definition(), LossCarryForward.class, row);
        if (carry.absorbedBy() != HeadCode.CAPITAL_GAINS) {
            throw new IllegalArgumentException(row + " carries a capital loss against " + carry.absorbedBy()
                    + ", but this set-off works on the capital-gains parts; the row is a pack defect (D-263)");
        }
        List<CarriedLoss> years = new ArrayList<>();
        ctx.inputs().ledgers().capitalLossCarriedForward().forEach((arose, carried) -> years.add(new CarriedLoss(arose,
                Optional.of(carried.determinedInReturnFiledInTime()), List.of(
                        new Portion(carried.longTermRemaining(), row, declared::canAbsorbLongTermLoss,
                                capitalLossText("long", arose, order)),
                        new Portion(carried.shortTermRemaining(), row, part -> true,
                                capitalLossText("short", arose, order))))));
        return new CarriedLosses(row, carry, new CarriedLossTexts("capital loss",
                "S2 s.111(2), s.536(2)(n); 1961 s.74(2)", "S2 s.111(1)(b); 1961 s.74(1)(c)",
                Optional.of("S2 s.121; 1961 s.80")), years);
    }

    private static Function<String, String> capitalLossText(String term, TaxYearId arose,
            CapitalGainsAbsorptionOrder order) {
        return part -> term + "-term loss of " + arose.label() + " against " + part + "; order: this year's set-off"
                + " first, then the oldest year, its long-term loss first, the parts " + order.parts() + " — the"
                + " declared default, the Acts prescribing none (D-247)";
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
