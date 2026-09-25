package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.Set;

/**
 * The total income rounded to the nearest ₹10 before any slab is walked — the first of the two rounding
 * points (S2 s.516; 1961 s.288A; {@code RATE-ROUND-003}).
 */
public final class RoundTotalIncomeNode implements ComputationNode {

    private static final RoundingStep STEP =
            new RoundingStep(NodeIds.ROUND_TOTALINCOME, NodeIds.TOTALINCOME, RuleIds.RATE_ROUND_TOTAL_INCOME);

    @Override
    public NodeId produces() {
        return STEP.produces();
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(STEP.reads());
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(STEP.rule());
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        return STEP.apply(ctx);
    }
}
