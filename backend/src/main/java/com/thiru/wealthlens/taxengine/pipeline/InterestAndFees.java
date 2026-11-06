package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;

/**
 * Interest and fees owed on the return, each by the row that charges it (2025 ss.423 – 425, 428; 1961 ss.234A – 234C, 234F;
 * W-112). Kept as items, not one total, because interest grows while a person waits and a fee does not (DR 15 C1).
 *
 * @param items one per provision that charges something; a provision that charges nothing has none
 */
public record InterestAndFees(List<Item> items) implements NodeValue {

    public InterestAndFees {
        if (items == null) {
            throw new IllegalArgumentException("Interest and fees need a list of items, empty where nothing is owed");
        }
        items = List.copyOf(items);
    }

    @Override
    public Money asMoney() {
        return items.stream().map(Item::amount).reduce(Money.ZERO, Money::plus);
    }

    /** Whether an item is interest or a fee. */
    public enum Kind {
        INTEREST,
        FEE
    }

    /**
     * What one provision charges.
     *
     * @param rule the row that charges it
     * @param kind interest or fee
     * @param amount the amount, above nil
     */
    public record Item(RuleId rule, Kind kind, Money amount) {
    }
}
