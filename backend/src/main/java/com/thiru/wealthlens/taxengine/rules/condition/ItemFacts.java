package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import java.util.Map;

/**
 * The facts of the one item a node is computing, as codes — what an {@link ItemFactIs} reads (W-088m2, D-253).
 *
 * <p>A fact the item does not carry <b>throws</b> when asked for, as every lookup in {@link ConditionContext} does:
 * answering "no" would silently choose another row for the item.
 *
 * @param codes each fact's code, such as {@code vehicle.engine} → {@code UP_TO_1600_CC}
 */
public record ItemFacts(Map<ItemFactId, String> codes) {

    /** No item — a condition evaluated about the person and the computation only. */
    public static final ItemFacts NONE = new ItemFacts(Map.of());

    public ItemFacts {
        if (codes == null) {
            throw new IllegalArgumentException("ItemFacts needs its codes, empty for none");
        }
        codes = Map.copyOf(codes);
    }

    /** An item with one fact. */
    public static ItemFacts of(ItemFactId fact, String code) {
        return new ItemFacts(Map.of(fact, code));
    }

    /**
     * The item's code for a fact.
     *
     * @throws IllegalArgumentException if the item does not carry that fact
     */
    public String code(ItemFactId fact) {
        String code = codes.get(fact);
        if (code == null) {
            throw new IllegalArgumentException("The item carries no fact " + fact + "; it carries " + codes.keySet());
        }
        return code;
    }
}
