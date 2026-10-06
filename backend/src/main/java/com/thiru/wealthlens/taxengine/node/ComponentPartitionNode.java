package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Partitions the entered pay lines into heads by the year's registry ({@code DR 06} G1.1 step 1, D-090).
 *
 * <p>{@code D-CLOSEDSUM}: a head's gross is a sum over a <em>declared</em> partition, so a code the year's
 * registry does not hold — misspelled, invented, or not live that year — stops the run here. O-15 was exactly
 * the opposite: {@code EMPLOR_PF}, well-formed and unknown, summed silently into gross.
 *
 * <p>No rule states the partition; it is how the engine keeps that promise. So the node declares no rule and
 * leaves no trace line — the heads' own computations explain their figures.
 */
public final class ComponentPartitionNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.INPUTS_COMPONENTS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Map<HeadCode, List<ComponentSet.Line>> byHead = new EnumMap<>(HeadCode.class);
        List<Employment> employments = ctx.inputs().entered().employments();
        for (int position = 0; position < employments.size(); position++) {
            for (SalaryComponent component : employments.get(position).components()) {
                byHead.computeIfAbsent(headOf(definitionOf(ctx, component, position), position),
                                head -> new ArrayList<>())
                        .add(new ComponentSet.Line(position, component.code(), component.annualAmount()));
            }
        }
        return NodeResult.untraced(new ComponentSet(byHead));
    }

    /** The head a pay line belongs to; a qualifying payment has none and is claimed, not entered as pay (D-130). */
    private static HeadCode headOf(ComponentDefinition definition, int position) {
        return definition.head().orElseThrow(() -> new IllegalArgumentException("Component " + definition.code()
                + " in employment " + position + " is a " + definition.kind() + ", income under no head; it is"
                + " claimed as a deduction, not entered as pay (D-130)"));
    }

    /** The registry entry live this year, or a refusal — never a line summed in unknown. */
    private static ComponentDefinition definitionOf(NodeContext ctx, SalaryComponent component, int position) {
        TaxYearId taxYear = ctx.inputs().taxYear();
        ComponentDefinition definition = ctx.pack().components().get(component.code());
        if (definition == null || !definition.lifecycle().isLiveIn(taxYear)) {
            throw new IllegalStateException("Component " + component.code() + " in employment " + position
                    + " is not in the " + taxYear.label() + " registry (unknown, or not live that year); it is"
                    + " refused rather than summed into a head it may not belong to (D-CLOSEDSUM)");
        }
        return definition;
    }
}
