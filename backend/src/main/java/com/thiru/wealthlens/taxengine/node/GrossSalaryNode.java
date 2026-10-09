package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.AgniveerCorpusFund;
import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gross salary: what "salary includes" (S2 s.16; 1961 s.17(1)) adds up to for this taxpayer ({@code DR 06}
 * G1.1 step 2, D-091).
 *
 * <p>The row states the definition — the total of one head — and the registry decides which components are
 * under that head and which of them are salary (D-089, D-093), so this node adds the partition's lines that
 * feed the definition and names neither a head nor a component itself. Perquisites — "salary" includes them (S2
 * s.16(e); 1961 s.17(1)(iv)) — are valued by {@code salary.perquisites} and added on a line of their own (D-111);
 * employer-contribution charges beyond their limit (D-126) are added on a line of their own too, and so are the
 * employer's pension-scheme contribution and the Government's Agniveer Corpus Fund contribution, which "salary"
 * includes (S2 s.16(k), (l); 1961 s.17(1)(viii), (ix); D-140).
 */
public final class GrossSalaryNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_GROSS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS, NodeIds.SALARY_PERQUISITES, NodeIds.EMPLOYER_CONTRIB_CHARGES);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.SAL_SALARY_INCLUDES);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        ComponentSet components = ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class);
        RuleVersion version = ctx.rule(RuleIds.SAL_SALARY_INCLUDES).versionAt(ctx.inputs().temporal());
        HeadTotal total = salaryIncludes(ctx, version);
        HeadTotalLines counted = HeadTotalLines.of(ctx, components, total, RuleIds.SAL_SALARY_INCLUDES);
        Money pay = counted.total();
        TraceLine payLine = TraceLine.of(1, produces(), RuleIds.SAL_SALARY_INCLUDES, StepKind.INCOME,
                counted.asComponentSet(total), MoneyValue.of(pay), version.provenance());
        List<TraceLine> lines = new ArrayList<>(List.of(payLine));
        Money gross = pay
                .plus(added(lines, version, ctx.get(NodeIds.SALARY_PERQUISITES, MoneyValue.class).asMoney(),
                        "perquisites, valued in salary.perquisites"))
                .plus(added(lines, version, ctx.get(NodeIds.EMPLOYER_CONTRIB_CHARGES, MoneyValue.class).asMoney(),
                        "employer retirement contributions beyond the limit, in employer.contrib.charges"))
                .plus(added(lines, version, pensionContributions(ctx),
                        "the employer's contribution to the employee's pension-scheme account"))
                .plus(added(lines, version, ctx.inputs().entered().agniveerCorpusFund()
                                .map(AgniveerCorpusFund::governmentContribution).orElse(Money.ZERO),
                        "the Central Government's contribution to the Agniveer Corpus Fund account"));
        return new NodeResult(MoneyValue.of(gross), lines);
    }

    /** An amount salary includes, on a line of its own where it is not nil; the amount added. */
    private static Money added(List<TraceLine> lines, RuleVersion version, Money amount, String what) {
        if (!amount.isGreaterThan(Money.ZERO)) {
            return Money.ZERO;
        }
        lines.add(TraceLine.of(lines.size() + 1, NodeIds.SALARY_GROSS, RuleIds.SAL_SALARY_INCLUDES, StepKind.INCOME,
                        MoneyValue.of(amount), MoneyValue.of(amount), version.provenance())
                .withLimitedBy(what));
        return amount;
    }

    /** The employers' contributions to the employee's pension-scheme account — salary (S2 s.16(k); 1961 s.17(1)(viii)). */
    private static Money pensionContributions(NodeContext ctx) {
        return ctx.inputs().entered().employerContributions().stream()
                .filter(contribution -> contribution.fund() == EmployerFund.NOTIFIED_PENSION_SCHEME)
                .map(EmployerFundContribution::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    /**
     * The pay under the head, perquisites apart: the lines {@code SAL-SALARY-INCLUDES} totals. Read also by the
     * specified-employee measure, which is salary "by way of monetary payment" (D-113). The caller declares
     * {@code inputs.components} and the rule.
     */
    static Money pay(NodeContext ctx) {
        RuleVersion version = ctx.rule(RuleIds.SAL_SALARY_INCLUDES).versionAt(ctx.inputs().temporal());
        return HeadTotalLines.of(ctx, ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class),
                salaryIncludes(ctx, version), RuleIds.SAL_SALARY_INCLUDES).total();
    }

    private static HeadTotal salaryIncludes(NodeContext ctx, RuleVersion version) {
        if (!ctx.inRegime(RuleIds.SAL_SALARY_INCLUDES, version).applies()) {
            throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES + " does not apply in regime "
                    + ctx.regime().code() + "; salary is defined in every regime, so the row is wrong");
        }
        return RuleDefinitions.require(version.definition(), HeadTotal.class, RuleIds.SAL_SALARY_INCLUDES);
    }
}
