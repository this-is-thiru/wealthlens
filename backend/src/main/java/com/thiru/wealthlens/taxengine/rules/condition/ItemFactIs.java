package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;

/**
 * A fact of the item being computed has a given code — how a row says which car, house or payment it is for
 * (W-088m2, D-253). Several codes are an {@link AnyOf}.
 *
 * @param fact the item's fact
 * @param code the code the row is for
 */
public record ItemFactIs(ItemFactId fact, String code) implements Condition {

    public ItemFactIs {
        if (fact == null || code == null || code.isBlank()) {
            throw new IllegalArgumentException("ItemFactIs needs a fact and a code");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.item().code(fact).equals(code);
    }

    @Override
    public String describe() {
        return fact + " is " + code;
    }
}
