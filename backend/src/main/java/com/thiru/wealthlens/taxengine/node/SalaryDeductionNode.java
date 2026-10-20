package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.RegimeVerdict;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The deductions from salary (S2 s.19(1) Table Sl. 1–2; 1961 s.16(ia), (ii), (iii); D-093, D-105): the tax on
 * employment in full, the standard deduction — the regime's statutory amount or the salary, whichever is less — and,
 * for TY 2025-26 only, a Government employee's entertainment allowance, at most one-fifth of the salary exclusive of
 * allowances or the Act's fixed limit.
 *
 * <p>Which regime allows what is each row's or the regime's, never this node's: the new regime forbids the tax on
 * employment (s.202(2)(a)(iv), through its disallowance set — D-165), and exactly one standard-deduction row applies
 * in any regime, because s.19 itself keys the amount on s.202(1). A row written for other regimes is not evaluated
 * and leaves no line; a tax on employment paid and disallowed leaves a nil line naming the provision.
 */
public final class SalaryDeductionNode implements ComputationNode {

    /** The base the standard deduction's "salary" limb reads: gross salary; the entertainment limb, its own base. */
    private static final BaseId SALARY = new BaseId("SALARY");

    /** The rules the deductions read — declared also by a node that applies them to another salary (D-113). */
    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_SAL_TAX_ON_EMPLOYMENT,
            RuleSetIds.RS_SAL_STANDARD_DEDUCTION, RuleSetIds.RS_SAL_ENTMT_ALLOWANCE);

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_DEDUCTIONS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS, NodeIds.SALARY_GROSS, NodeIds.SALARY_BASE);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return RULES;
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<TraceLine> trace = lines(ctx, ctx.get(NodeIds.SALARY_GROSS, MoneyValue.class).asMoney());
        return new NodeResult(MoneyValue.of(total(trace)), trace);
    }

    /**
     * The deductions from a salary other than gross salary — the specified employee's salary "by way of monetary
     * payment" (D-113), whose standard deduction is capped at that salary. The caller declares this node's rules and
     * its {@code inputs.components} and {@code salary.base}.
     */
    static Money on(NodeContext ctx, Money salary) {
        return total(lines(ctx, salary));
    }

    private static List<TraceLine> lines(NodeContext ctx, Money salary) {
        List<TraceLine> trace = new ArrayList<>();
        taxOnEmployment(ctx).ifPresent(trace::add);
        trace.add(standardDeduction(ctx, salary, trace.size() + 1));
        entertainmentAllowance(ctx, trace.size() + 1).ifPresent(trace::add);
        return trace;
    }

    private static Money total(List<TraceLine> trace) {
        return trace.stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
    }

    /**
     * The whole of the tax on employment entered, where the regime allows it; where the regime forbids it, a nil line
     * for what was paid, naming the provision (D-165).
     */
    private static Optional<TraceLine> taxOnEmployment(NodeContext ctx) {
        RuleVersion version = ctx.ruleOf(RuleSetIds.RS_SAL_TAX_ON_EMPLOYMENT).versionAt(ctx.inputs().temporal());
        RegimeVerdict verdict = ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_SAL_TAX_ON_EMPLOYMENT), version);
        if (verdict instanceof RegimeVerdict.ExcludedByRow) {
            return Optional.empty();
        }
        HeadTotal total = RuleDefinitions.require(version.definition(), HeadTotal.class, ctx.ruleIdOf(RuleSetIds.RS_SAL_TAX_ON_EMPLOYMENT));
        HeadTotalLines paid = HeadTotalLines.of(ctx, ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class), total,
                ctx.ruleIdOf(RuleSetIds.RS_SAL_TAX_ON_EMPLOYMENT));
        if (verdict.applies()) {
            return Optional.of(TraceLine.of(1, NodeIds.SALARY_DEDUCTIONS, ctx.ruleIdOf(RuleSetIds.RS_SAL_TAX_ON_EMPLOYMENT),
                    StepKind.DEDUCTION, paid.asComponentSet(total), MoneyValue.of(paid.total()), version.provenance()));
        }
        if (!(verdict instanceof RegimeVerdict.Disallowed disallowed) || !paid.total().isGreaterThan(Money.ZERO)) {
            return Optional.empty();
        }
        return Optional.of(TraceLine.of(1, NodeIds.SALARY_DEDUCTIONS, ctx.ruleIdOf(RuleSetIds.RS_SAL_TAX_ON_EMPLOYMENT),
                        StepKind.DEDUCTION, paid.asComponentSet(total), MoneyValue.of(Money.ZERO), version.provenance())
                .withLimitedBy(disallowed.whyNot()));
    }

    /**
     * A Government employee's entertainment allowance (1961 s.16(ii); D-105): the least of one-fifth of the salary
     * exclusive of allowances and the fixed limit, where an entertainment allowance was paid. None where the year's
     * Act has no such deduction, the row is written for other regimes, or no Government employment paid the
     * allowance; nil, naming the provision, where the regime forbids it (D-166).
     */
    private static Optional<TraceLine> entertainmentAllowance(NodeContext ctx, int sequence) {
        Optional<RuleRow> row = ctx.optionalRuleOf(RuleSetIds.RS_SAL_ENTMT_ALLOWANCE);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = row.get();
        RuleId ruleId = rule.id();
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        RegimeVerdict verdict = ctx.inRegime(ruleId, version);
        if (verdict instanceof RegimeVerdict.ExcludedByRow) {
            return Optional.empty();
        }
        Map<Integer, Money> paidByGovernment = paidByGovernment(ctx, ruleId);
        if (paidByGovernment.isEmpty()) {
            return Optional.empty();
        }
        if (paidByGovernment.size() > 1) {
            throw new IllegalArgumentException("An entertainment allowance is paid by " + paidByGovernment.size()
                    + " Government employments; the Act allows the assessee one sum and does not say whether it is"
                    + " per employment or in all, so enter one");
        }
        int employment = paidByGovernment.keySet().iterator().next();
        if (!verdict.applies()) {
            return Optional.of(TraceLine.of(sequence, NodeIds.SALARY_DEDUCTIONS, ruleId, StepKind.DEDUCTION,
                            MoneyValue.of(paidByGovernment.get(employment)), MoneyValue.of(Money.ZERO),
                            version.provenance())
                    .withLimitedBy(verdict.whyNot()));
        }
        Money salary = ctx.get(NodeIds.SALARY_BASE, SalaryBases.class)
                .of(ctx.pack().ruleSet(RuleSetIds.RS_SAL_SALARY_FOR_ENTMT), employment);
        CapResult deducted = TemporallyApportionedCap.apply(rule, t, new CapContext(Map.of(SALARY, salary)));
        return Optional.of(TraceLine.of(sequence, NodeIds.SALARY_DEDUCTIONS, ruleId, StepKind.DEDUCTION,
                        MoneyValue.of(paidByGovernment.get(employment)), MoneyValue.of(deducted.value()),
                        version.provenance())
                .withLimitedBy(deducted.limitedBy()));
    }

    /** The entertainment allowance each Government employment paid, by employment; none that paid nothing. */
    private static Map<Integer, Money> paidByGovernment(NodeContext ctx, RuleId ruleId) {
        ComponentSet components = ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class);
        List<Employment> employments = ctx.inputs().entered().employments();
        Map<Integer, Money> paidByGovernment = new TreeMap<>();
        for (ComponentSet.Line line : components.in(HeadCode.SALARIES)) {
            ComponentDefinition definition = ctx.pack().components().get(line.code());
            boolean allowance = definition != null && definition.feedsInto().contains(ruleId);
            if (allowance && employments.get(line.employment()).employerKind() == EmployerKind.GOVERNMENT) {
                paidByGovernment.merge(line.employment(), line.amount(), Money::plus);
            }
        }
        paidByGovernment.values().removeIf(amount -> !amount.isGreaterThan(Money.ZERO));
        return paidByGovernment;
    }

    /**
     * The regime's standard deduction, capped at the salary. s.19(1) Table Sl. 2 gives every regime exactly one; which
     * row is the regime's is the rule set's to say (D-252).
     */
    private static TraceLine standardDeduction(NodeContext ctx, Money salary, int sequence) {
        RuleRow row = ctx.ruleOf(RuleSetIds.RS_SAL_STANDARD_DEDUCTION);
        RuleVersion version = row.versionAt(ctx.inputs().temporal());
        CapResult deduction = TemporallyApportionedCap.apply(row, ctx.inputs().temporal(), CapContext.of(SALARY, salary));
        return TraceLine.of(sequence, NodeIds.SALARY_DEDUCTIONS, row.id(), StepKind.DEDUCTION,
                        MoneyValue.of(salary), MoneyValue.of(deduction.value()), version.provenance())
                .withLimitedBy(deduction.limitedBy());
    }
}
