package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Set;

/**
 * The amount payable before filing effects: the whole tax after rebate plus the surcharge and the cess
 * (D-080, D-086) — the figure {@code round.netpayable} rounds.
 *
 * <p>No rule states it; it is "an arithmetic sum of already-computed rows" ({@code REBATE-ORDER-001}),
 * each of which has its own line. So the node emits none, and the sum shows as the rounding line's input.
 *
 * <p>The Phase 9 credits item subtracts credits under its own rule.
 */
public final class NetPayableNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.TAX_NETPAYABLE;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.TAX_AFTERREBATE, NodeIds.TAX_SURCHARGE, NodeIds.TAX_CESS);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money tax = ctx.get(NodeIds.TAX_AFTERREBATE, TaxByBasis.class).asMoney();
        Money surcharge = ctx.get(NodeIds.TAX_SURCHARGE, MoneyValue.class).amount();
        Money cess = ctx.get(NodeIds.TAX_CESS, MoneyValue.class).amount();
        return NodeResult.untraced(MoneyValue.of(tax.plus(surcharge).plus(cess)));
    }
}
