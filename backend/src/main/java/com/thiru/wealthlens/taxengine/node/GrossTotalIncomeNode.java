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
 * <p>Salaries is the only head the engine computes, so today it is income under that head. Phase 9 brings the other
 * heads and their set-off, and this node then reads {@code setoff} instead; its consumers do not change. Untraced:
 * the head's figure already carries its lines.
 */
public final class GrossTotalIncomeNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.GTI;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SALARY_INCOME);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        return NodeResult.untraced(MoneyValue.of(ctx.get(NodeIds.SALARY_INCOME, MoneyValue.class).asMoney()));
    }
}
