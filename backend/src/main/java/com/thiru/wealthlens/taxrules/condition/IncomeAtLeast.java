package com.thiru.wealthlens.taxrules.condition;

import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.money.Money;

/**
 * A computed amount reaches a threshold.
 *
 * <p>Inclusive at the boundary, matching {@link IncomeAtMost}. The surcharge ladders are the
 * cautionary case: {@code RATE-SUR-NEW-2026} charges where income <em>exceeds</em> ₹50,00,000, so a
 * rule using this predicate for that threshold must state ₹50,00,001 — or, better, use the ladder,
 * which already models the boundary correctly.
 *
 * @param node which computed amount to read
 * @param threshold the amount that must be reached, inclusive
 */
public record IncomeAtLeast(NodeId node, Money threshold) implements Condition {

    public IncomeAtLeast {
        if (node == null || threshold == null) {
            throw new IllegalArgumentException("IncomeAtLeast needs both a node and a threshold");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return !ctx.valueOf(node).isLessThan(threshold);
    }

    @Override
    public String describe() {
        return node + " is at least " + threshold;
    }
}
