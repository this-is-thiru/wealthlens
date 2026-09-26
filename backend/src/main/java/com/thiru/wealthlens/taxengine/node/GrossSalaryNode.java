package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gross salary: what "salary includes" (S2 s.16; 1961 s.17(1)) adds up to for this taxpayer ({@code DR 06}
 * G1.1 step 2, D-091).
 *
 * <p>The row states the definition — the total of one head — and the registry decides which components are
 * under that head (D-089), so this node adds the partition's lines for it and names neither a head nor a
 * component itself. Perquisites and employer-contribution charges join the sum with their own items;
 * {@code SAL-CHARGE-001}'s due-or-paid test is {@code W-065}'s.
 */
public final class GrossSalaryNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_GROSS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.SAL_SALARY_INCLUDES);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        ComponentSet components = ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class);
        RuleVersion version = ctx.rule(RuleIds.SAL_SALARY_INCLUDES).versionAt(ctx.inputs().temporal());
        if (!version.regimes().appliesIn(ctx.regime().code())) {
            throw new IllegalStateException(RuleIds.SAL_SALARY_INCLUDES + " does not apply in regime "
                    + ctx.regime().code() + "; salary is defined in every regime, so the row is wrong");
        }
        RuleDefinition definition = version.definition().orElseThrow(() -> new IllegalStateException(
                RuleIds.SAL_SALARY_INCLUDES + " carries no definition; gross salary has nothing to total"));
        HeadTotal total = switch (definition) {
            case HeadTotal headTotal -> headTotal;
        };
        List<ComponentSet.Line> lines = components.in(total.head());
        Money gross = lines.stream().map(ComponentSet.Line::amount).reduce(Money.ZERO, Money::plus);
        ComponentSet counted = new ComponentSet(lines.isEmpty() ? Map.of() : Map.of(total.head(), lines));
        return NodeResult.of(MoneyValue.of(gross), TraceLine.of(1, produces(), RuleIds.SAL_SALARY_INCLUDES,
                StepKind.INCOME, counted, MoneyValue.of(gross), version.provenance()));
    }
}
