package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import java.time.LocalDate;
import java.util.Map;

/**
 * The facts of the one item a node is computing — codes an {@link ItemFactIs} reads, quantities an
 * {@link ItemFactAtMost} reads, dates an {@link ItemDateBefore} reads (W-088m2, W-088n1, W-088f2b; D-253, D-257,
 * D-291).
 *
 * <p>A fact the item does not carry <b>throws</b> when asked for, as every lookup in {@link ConditionContext} does:
 * answering "no" would silently choose another row for the item.
 *
 * @param codes each categorical fact's code, such as {@code accommodation.provider} → {@code HOTEL}
 * @param quantities each counted fact, such as {@code vehicle.engine.cc} → {@code 1500}
 * @param dates each dated fact, such as {@code capitalgains.acquired.on} → {@code 2004-09-30}
 */
public record ItemFacts(
        Map<ItemFactId, String> codes, Map<ItemFactId, Long> quantities, Map<ItemFactId, LocalDate> dates) {

    /** No item — a condition evaluated about the person and the computation only. */
    public static final ItemFacts NONE = new ItemFacts(Map.of(), Map.of(), Map.of());

    public ItemFacts {
        if (codes == null || quantities == null || dates == null) {
            throw new IllegalArgumentException("ItemFacts needs its codes, quantities and dates, empty for none");
        }
        codes = Map.copyOf(codes);
        quantities = Map.copyOf(quantities);
        dates = Map.copyOf(dates);
    }

    /** An item with one categorical fact. */
    public static ItemFacts of(ItemFactId fact, String code) {
        return new ItemFacts(Map.of(fact, code), Map.of(), Map.of());
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

    /**
     * The item's quantity for a fact.
     *
     * @throws IllegalArgumentException if the item does not carry that quantity
     */
    public long quantity(ItemFactId fact) {
        Long quantity = quantities.get(fact);
        if (quantity == null) {
            throw new IllegalArgumentException("The item carries no quantity " + fact + "; it carries "
                    + quantities.keySet());
        }
        return quantity;
    }

    /**
     * The item's date for a fact.
     *
     * @throws IllegalArgumentException if the item does not carry that date
     */
    public LocalDate date(ItemFactId fact) {
        LocalDate date = dates.get(fact);
        if (date == null) {
            throw new IllegalArgumentException("The item carries no date " + fact + "; it carries " + dates.keySet());
        }
        return date;
    }
}
