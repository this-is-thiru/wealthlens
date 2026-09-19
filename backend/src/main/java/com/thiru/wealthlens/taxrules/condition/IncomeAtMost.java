package com.thiru.wealthlens.taxrules.condition;

import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.money.Money;

/**
 * A computed amount does not exceed a limit.
 *
 * <p>Inclusive at the boundary, because the statute's thresholds are written that way — the rebate
 * applies where total income <em>does not exceed</em> its threshold, not where it is strictly below.
 * An off-by-one here moves a taxpayer exactly on the line from a nil bill to a full one.
 *
 * @param node which computed amount to read
 * @param limit the threshold, inclusive
 */
public record IncomeAtMost(NodeId node, Money limit) implements Condition {

    public IncomeAtMost {
        if (node == null || limit == null) {
            throw new IllegalArgumentException("IncomeAtMost needs both a node and a limit");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.valueOf(node).isLessThanOrEqual(limit);
    }

    @Override
    public String describe() {
        return node + " is at most " + limit;
    }
}
