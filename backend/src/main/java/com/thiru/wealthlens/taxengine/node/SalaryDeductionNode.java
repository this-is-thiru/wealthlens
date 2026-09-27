package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.JourneysPerCalendarBlock;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The deductions from salary (S2 s.19(1) Table Sl. 1–2; 1961 s.16(ia), (iii); D-093): the tax on employment
 * in full, and the standard deduction — the regime's statutory amount or the salary, whichever is less.
 *
 * <p>Which regime allows what is each row's, never this node's: the tax on employment applies everywhere but the
 * new regime (s.202(2)(a)(iv)), and exactly one standard-deduction row applies in any regime, because s.19
 * itself keys the amount on s.202(1). A row that does not apply in the regime is not evaluated and leaves no line.
 */
public final class SalaryDeductionNode implements ComputationNode {

    /** The base the standard deduction's "salary" limb reads: gross salary. */
    private static final BaseId SALARY = new BaseId("SALARY");

    private static final List<RuleId> STANDARD_DEDUCTIONS = List.of(
            RuleIds.SAL_STANDARD_DEDUCTION_NEW_TAX_REGIME, RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE);

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_DEDUCTIONS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS, NodeIds.SALARY_GROSS);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.SAL_TAX_ON_EMPLOYMENT, RuleIds.SAL_STANDARD_DEDUCTION_NEW_TAX_REGIME,
                RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<TraceLine> trace = new ArrayList<>();
        taxOnEmployment(ctx).ifPresent(trace::add);
        trace.add(standardDeduction(ctx, trace.size() + 1));
        Money total = trace.stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
        return new NodeResult(MoneyValue.of(total), trace);
    }

    /** The whole of the tax on employment entered, where the regime allows it. */
    private static Optional<TraceLine> taxOnEmployment(NodeContext ctx) {
        RuleVersion version = ctx.rule(RuleIds.SAL_TAX_ON_EMPLOYMENT).versionAt(ctx.inputs().temporal());
        if (!version.regimes().appliesIn(ctx.regime().code())) {
            return Optional.empty();
        }
        HeadTotal total = switch (version.definition().orElseThrow(() -> new IllegalStateException(
                RuleIds.SAL_TAX_ON_EMPLOYMENT + " carries no definition; there is no amount to deduct"))) {
            case HeadTotal headTotal -> headTotal;
            case SalaryBase salaryBase -> throw new IllegalStateException(
                    RuleIds.SAL_TAX_ON_EMPLOYMENT + " defines a salary base, not the total of what was paid");
            case JourneysPerCalendarBlock journeys -> throw new IllegalStateException(
                    RuleIds.SAL_TAX_ON_EMPLOYMENT + " defines journeys per block, not the total of what was paid");
        };
        HeadTotalLines paid = HeadTotalLines.of(ctx, ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class), total,
                RuleIds.SAL_TAX_ON_EMPLOYMENT);
        return Optional.of(TraceLine.of(1, NodeIds.SALARY_DEDUCTIONS, RuleIds.SAL_TAX_ON_EMPLOYMENT,
                StepKind.DEDUCTION, paid.asComponentSet(total), MoneyValue.of(paid.total()), version.provenance()));
    }

    /** The regime's standard deduction, capped at the salary; exactly one row may apply. */
    private static TraceLine standardDeduction(NodeContext ctx, int sequence) {
        List<RuleVersion> applying = new ArrayList<>();
        List<RuleId> rules = new ArrayList<>();
        for (RuleId rule : STANDARD_DEDUCTIONS) {
            RuleVersion version = ctx.rule(rule).versionAt(ctx.inputs().temporal());
            if (version.regimes().appliesIn(ctx.regime().code())) {
                applying.add(version);
                rules.add(rule);
            }
        }
        if (applying.size() != 1) {
            throw new IllegalStateException("Regime " + ctx.regime().code() + " has " + rules
                    + " as its standard deduction; s.19(1) Table Sl. 2 gives every regime exactly one");
        }
        RuleVersion version = applying.getFirst();
        Money salary = ctx.get(NodeIds.SALARY_GROSS, MoneyValue.class).asMoney();
        CapResult deduction = TemporallyApportionedCap.apply(
                ctx.rule(rules.getFirst()), ctx.inputs().temporal(), CapContext.of(SALARY, salary));
        return TraceLine.of(sequence, NodeIds.SALARY_DEDUCTIONS, rules.getFirst(), StepKind.DEDUCTION,
                        MoneyValue.of(salary), MoneyValue.of(deduction.value()), version.provenance())
                .withLimitedBy(deduction.limitedBy());
    }
}
