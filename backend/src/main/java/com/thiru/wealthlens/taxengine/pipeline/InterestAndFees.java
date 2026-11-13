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

    /** What the person owes: interest and fees, never the interest added to a refund, which is paid to them (W-112b C4a). */
    @Override
    public Money asMoney() {
        return items.stream().filter(item -> item.kind() != Kind.RECEIVABLE).map(Item::amount).reduce(Money.ZERO, Money::plus);
    }

    /** Whether an item is interest or a fee the person owes, or interest the Government adds to their refund. */
    public enum Kind {
        INTEREST,
        FEE,
        /** Interest added to a refund (2025 s.437; 1961 s.244A; W-112b): paid to the person, so in no amount payable. */
        RECEIVABLE
    }

    /**
     * What one provision charges, or adds to a refund.
     *
     * @param rule the row that charges or adds it
     * @param kind interest, fee or receivable
     * @param amount the amount, above nil
     */
    public record Item(RuleId rule, Kind kind, Money amount) {
    }
}
