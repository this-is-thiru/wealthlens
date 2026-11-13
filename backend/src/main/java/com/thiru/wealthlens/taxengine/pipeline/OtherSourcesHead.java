package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * Income from other sources: the head, and the gross income chargeable by kind as the return lays it out (W-093a C1a,
 * D-658). Schedule OS prints its rows by kind — dividends, interest on savings, on deposits, family pension — not by
 * item, so the items of one kind are added together; what is deducted against them is read from their rows' lines.
 *
 * @param total the head's income
 * @param grossByKind the gross income chargeable, by kind: each entered item's kind by its name, and
 *     {@code PROVIDENT_FUND_INTEREST}, {@code GIFTS}, {@code BUSINESS_TRUST} and {@code LIFE_POLICY} for the sums
 *     entered apart from items; a kind not received is absent
 */
public record OtherSourcesHead(Money total, Map<String, Money> grossByKind) implements NodeValue {

    public OtherSourcesHead {
        if (total == null || grossByKind == null) {
            throw new IllegalArgumentException("An other-sources head needs its total and its gross by kind, empty for none");
        }
        grossByKind = Map.copyOf(grossByKind);
    }

    @Override
    public Money asMoney() {
        return total;
    }
}
