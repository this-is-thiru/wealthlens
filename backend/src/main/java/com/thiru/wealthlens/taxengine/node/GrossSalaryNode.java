package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.DayWindow;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.ExcludedAssets;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.IndexByYear;
import com.thiru.wealthlens.taxengine.rules.definition.JourneysPerCalendarBlock;
import com.thiru.wealthlens.taxengine.rules.definition.PremiumShareBands;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
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
 * employer-contribution charges beyond their limit (D-126) are added on a line of their own too.
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
        Money gross = pay;
        Money perquisites = ctx.get(NodeIds.SALARY_PERQUISITES, MoneyValue.class).asMoney();
        if (perquisites.isGreaterThan(Money.ZERO)) {
            gross = gross.plus(perquisites);
            lines.add(TraceLine.of(lines.size() + 1, produces(), RuleIds.SAL_SALARY_INCLUDES, StepKind.INCOME,
                            MoneyValue.of(perquisites), MoneyValue.of(perquisites), version.provenance())
                    .withLimitedBy("perquisites, valued in salary.perquisites"));
        }
        Money contributions = ctx.get(NodeIds.EMPLOYER_CONTRIB_CHARGES, MoneyValue.class).asMoney();
        if (contributions.isGreaterThan(Money.ZERO)) {
            gross = gross.plus(contributions);
            lines.add(TraceLine.of(lines.size() + 1, produces(), RuleIds.SAL_SALARY_INCLUDES, StepKind.INCOME,
                            MoneyValue.of(contributions), MoneyValue.of(contributions), version.provenance())
                    .withLimitedBy("employer retirement contributions beyond the limit, in employer.contrib.charges"));
        }
        return new NodeResult(MoneyValue.of(gross), lines);
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
        if (!version.regimes().appliesIn(ctx.regime().code())) {
            throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES + " does not apply in regime "
                    + ctx.regime().code() + "; salary is defined in every regime, so the row is wrong");
        }
        RuleDefinition definition = version.definition().orElseThrow(() -> new IllegalStateException(
                RuleIds.SAL_SALARY_INCLUDES + " carries no definition; gross salary has nothing to total"));
        return switch (definition) {
            case HeadTotal headTotal -> headTotal;
            case SalaryBase salaryBase -> throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES
                    + " defines a salary base, not a head's total; gross salary is the whole head");
            case JourneysPerCalendarBlock journeys -> throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES
                    + " defines journeys per block, not a head's total");
            case CommutedPensionShare share -> throw new IllegalStateException(
                    RuleIds.SAL_SALARY_INCLUDES + " defines a share of a commuted pension, not a head's total");
            case DisabledTransportLimit transport -> throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES
                    + " defines a transport limit, not a head's total");
            case IndexByYear index -> throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES
                    + " defines an index table, not a head's total");
            case ExcludedAssets excluded -> throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES
                    + " defines excluded assets, not a head's total");
            case DayWindow window -> throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES
                    + " defines a window of days, not a head's total");
            case PremiumShareBands bands -> throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES
                    + " defines premium share bands, not a head's total");
        };
    }
}
