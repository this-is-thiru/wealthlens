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
 * Total income: gross total income less the Chapter VIII deductions, which s.122(2) already keeps within it (S2
 * s.122(1); D-132). Untraced: both figures carry their own lines.
 */
public final class TotalIncomeNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.TOTALINCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.GTI, NodeIds.CHAPTERVIII);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        return NodeResult.untraced(MoneyValue.of(ctx.get(NodeIds.GTI, MoneyValue.class).asMoney()
                .minus(ctx.get(NodeIds.CHAPTERVIII, MoneyValue.class).asMoney())));
    }
}
