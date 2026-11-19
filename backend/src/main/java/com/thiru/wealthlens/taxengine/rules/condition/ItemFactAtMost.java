package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;

/**
 * A counted fact of the item being computed does not exceed a limit — the Rules' "does not exceed"; their "exceeding"
 * is its {@link Not} (W-088n1, D-257).
 *
 * <p>Inclusive at the boundary, as {@link IncomeAtMost} is and for the same reason: the Table's words are "does not
 * exceed", so a car of exactly the limit is in the lower column.
 *
 * @param fact the item's counted fact, such as an engine's capacity in cc
 * @param limit the limit, inclusive
 */
public record ItemFactAtMost(ItemFactId fact, long limit) implements Condition {

    public ItemFactAtMost {
        if (fact == null) {
            throw new IllegalArgumentException("ItemFactAtMost needs a fact");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.item().quantity(fact) <= limit;
    }

    @Override
    public String describe() {
        return fact + " is at most " + limit;
    }
}
