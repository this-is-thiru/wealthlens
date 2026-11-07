package com.thiru.wealthlens.taxengine.node;

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
 * What is left after credits: above nil it is payable, below nil refundable. Kept apart from {@code tax.netpayable}, which
 * stays the bill, because elections compare the bill and tax already paid must not move that comparison (D-436 C1a).
 * {@link RoundNetPayableNode} rounds this. Untraced, as {@link NetPayableNode} is: no rule states a subtraction.
 */
public final class BalanceNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.TAX_BALANCE;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.TAX_NETPAYABLE, NodeIds.CREDITS);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money bill = ctx.get(NodeIds.TAX_NETPAYABLE, MoneyValue.class).amount();
        Money credits = ctx.get(NodeIds.CREDITS, MoneyValue.class).amount();
        return NodeResult.untraced(MoneyValue.of(bill.minus(credits)));
    }
}
