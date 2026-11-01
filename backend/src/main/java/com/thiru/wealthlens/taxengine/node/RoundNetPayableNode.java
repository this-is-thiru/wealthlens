package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import java.util.Set;

/**
 * The amount payable or refundable rounded to the nearest ₹10 — the second and last rounding point
 * (S2 s.516; 1961 s.288B; {@code RATE-ROUND-003}). A refund is rounded on its magnitude with the sign
 * restored ({@code ROUND-NEG-001}, STATUS UNCLEAR).
 *
 * <p>Reads {@code tax.balance}, the bill after credits, so the amount rounded is the final one and the trace keeps its two
 * {@code ROUNDING} lines (PRD F2.4; D-436). {@code W-112} adds {@code interest.and.fees} when it
 * builds that node (D-076).
 */
public final class RoundNetPayableNode implements ComputationNode {

    private static final RoundingStep STEP =
            new RoundingStep(NodeIds.ROUND_NETPAYABLE, NodeIds.TAX_BALANCE, RuleSetIds.RS_RATE_ROUND_PAYABLE);

    @Override
    public NodeId produces() {
        return STEP.produces();
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(STEP.reads());
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(STEP.ruleSet());
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        return STEP.apply(ctx);
    }
}
