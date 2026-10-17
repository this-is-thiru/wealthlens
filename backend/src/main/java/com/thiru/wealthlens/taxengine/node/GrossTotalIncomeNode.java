package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.Set;

/**
 * Gross total income: the total income before any Chapter VIII deduction (S2 s.122(10); D-132).
 *
 * <p>Salaries, house property and other sources are the heads the engine computes; it is their income after a head's
 * loss is set off against the others ({@code setoff}, D-217). The business and capital-gains heads join through set-off
 * when they are built; this node's consumers do not change. Untraced: set-off carries the lines.
 */
public final class GrossTotalIncomeNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.GTI;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SETOFF);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        return NodeResult.untraced(ctx.get(NodeIds.SETOFF, MoneyValue.class));
    }
}
