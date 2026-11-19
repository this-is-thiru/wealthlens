package com.thiru.wealthlens.taxengine.golden;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Set;

/**
 * The golden run's one stand-in: the total income a T1 case states, in the slot the not-yet-built head
 * and deduction nodes will fill (D-083). Everything after it is the real engine.
 */
record GivenTotalIncome(Money amount) implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.TOTALINCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        return NodeResult.untraced(MoneyValue.of(amount));
    }
}
