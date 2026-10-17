package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.RegimeVerdict;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.AbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Income under the heads after a head's loss is set off against the others in the year (S2 s.109; 1961 s.71;
 * {@code DR 01} R18; D-217).
 *
 * <p>Each head's loss, taken in the declared {@link AbsorptionOrder}, is absorbed by the other heads in that order —
 * a house-property loss only up to the {@code SETOFF-HOUSE-PROPERTY} limit, and not at all where the regime forbids it
 * (S2 s.202(2)(b)(ii); 1961 s.115BAC(2)(ii)(b)). Every set-off is a line naming the order, which neither Act
 * prescribes (OQ-28). What is not set off is a MEASURE line: a house-property loss carried forward (W-081b) or, in the
 * new regime, deemed given full effect to and gone (S2 s.202(3)); a loss under other sources, which no provision
 * carries. The value is the heads' income after set-off — what gross total income adds up.
 */
public final class SetOffNode implements ComputationNode {

    private static final BaseId LOSS = new BaseId("LOSS");

    @Override
    public NodeId produces() {
        return NodeIds.SETOFF;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SALARY_INCOME, NodeIds.HOUSEPROPERTY_INCOME, NodeIds.OTHERSOURCES_INCOME);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.SETOFF_ACROSS_HEADS, RuleIds.SETOFF_HOUSE_PROPERTY);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        TemporalContext t = ctx.inputs().temporal();
        Map<HeadCode, Money> heads = new EnumMap<>(HeadCode.class);
        heads.put(HeadCode.SALARIES, ctx.get(NodeIds.SALARY_INCOME, MoneyValue.class).asMoney());
        heads.put(HeadCode.HOUSE_PROPERTY, ctx.get(NodeIds.HOUSEPROPERTY_INCOME, MoneyValue.class).asMoney());
        heads.put(HeadCode.OTHER_SOURCES, ctx.get(NodeIds.OTHERSOURCES_INCOME, MoneyValue.class).asMoney());
        AbsorptionOrder order = RuleDefinitions.require(version(ctx, t, RuleIds.SETOFF_ACROSS_HEADS).definition(),
                AbsorptionOrder.class, RuleIds.SETOFF_ACROSS_HEADS);
        Map<HeadCode, Money> income = new EnumMap<>(HeadCode.class);
        heads.forEach((head, amount) -> income.put(head, Money.max(amount, Money.ZERO)));
        List<TraceLine> lines = new ArrayList<>();
        for (HeadCode lossHead : order.heads()) {
            Money loss = Money.ZERO.minus(heads.getOrDefault(lossHead, Money.ZERO));
            if (loss.isGreaterThan(Money.ZERO)) {
                setOff(ctx, t, order, lossHead, loss, income, lines);
            }
        }
        Money total = income.values().stream().reduce(Money.ZERO, Money::plus);
        return new NodeResult(MoneyValue.of(total), lines);
    }

    /** One head's loss: what may be set off, set off against the others in order, and what is left. */
    private static void setOff(NodeContext ctx, TemporalContext t, AbsorptionOrder order, HeadCode lossHead,
            Money loss, Map<HeadCode, Money> income, List<TraceLine> lines) {
        boolean houseProperty = lossHead == HeadCode.HOUSE_PROPERTY;
        RuleId row = houseProperty ? RuleIds.SETOFF_HOUSE_PROPERTY : RuleIds.SETOFF_ACROSS_HEADS;
        RuleVersion version = version(ctx, t, row);
        RegimeVerdict verdict = ctx.inRegime(row, version);
        if (!verdict.applies()) {
            lines.add(line(lines, row, version, StepKind.SET_OFF, loss, Money.ZERO).withLimitedBy(verdict.whyNot()));
            lines.add(line(lines, row, version, StepKind.MEASURE, loss, loss).withLimitedBy("deemed to have been given"
                    + " full effect to, and not carried forward (S2 s.202(3); 1961 s.115BAC(3)): " + verdict.whyNot()));
            return;
        }
        Money allowed = loss;
        if (houseProperty) {
            CapResult limit = TemporallyApportionedCap.apply(ctx.rule(row), t, CapContext.of(LOSS, loss));
            allowed = limit.value();
            lines.add(line(lines, row, version, StepKind.MEASURE, loss, allowed).withLimitedBy(limit.limitedBy()));
        }
        Money setOff = absorb(order, lossHead, allowed, income, lines, row, version);
        Money left = loss.minus(setOff);
        if (left.isGreaterThan(Money.ZERO)) {
            lines.add(line(lines, row, version, StepKind.MEASURE, loss, left).withLimitedBy(houseProperty
                    ? "not set off: carried forward against income from house property (S2 s.110; 1961 s.71B) — W-081b"
                    : "not set off, and not carried forward: S2 ss.110–115 carry no loss under other sources"));
        }
    }

    /** The other heads absorb up to {@code allowed} of the loss, in order; returns how much they took. */
    private static Money absorb(AbsorptionOrder order, HeadCode lossHead, Money allowed, Map<HeadCode, Money> income,
            List<TraceLine> lines, RuleId row, RuleVersion version) {
        Money remaining = allowed;
        for (HeadCode head : order.heads()) {
            Money available = income.getOrDefault(head, Money.ZERO);
            if (head == lossHead || !remaining.isGreaterThan(Money.ZERO) || !available.isGreaterThan(Money.ZERO)) {
                continue;
            }
            Money taken = Money.min(remaining, available);
            income.put(head, available.minus(taken));
            remaining = remaining.minus(taken);
            lines.add(line(lines, row, version, StepKind.SET_OFF, available, taken).withLimitedBy(lossHead
                    + " loss against " + head + "; order " + order.heads() + ", the Act prescribing none (OQ-28)"));
        }
        return allowed.minus(remaining);
    }

    private static RuleVersion version(NodeContext ctx, TemporalContext t, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), t);
    }

    private static TraceLine line(List<TraceLine> lines, RuleId row, RuleVersion version, StepKind kind, Money in,
            Money out) {
        return TraceLine.of(lines.size() + 1, NodeIds.SETOFF, row, kind, MoneyValue.of(in), MoneyValue.of(out),
                version.provenance());
    }
}
