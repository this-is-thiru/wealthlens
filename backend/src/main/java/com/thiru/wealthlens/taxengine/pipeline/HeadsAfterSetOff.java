package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * The heads once every loss is set off against the others (D-243): their total, and what each capital-gains part still
 * holds — the special-rate parts are taxed on their own basis, so the total alone would lose them.
 *
 * <p>Each part is carried twice, as {@link CapitalGainsHead} carries it: the gain, and the gain with a qualifying cost
 * lifted by the Cost Inflation Index, which the cap on the tax on land or building compares a second rate against
 * (W-088g, D-426). The two maps are lowered by the same rupee amount whenever a loss is absorbed, so they stay key for
 * key with each other.
 *
 * @param total every head's income after set-off, which gross total income is
 * @param capitalGains each capital-gains part held, after other heads' losses, nil or more; a part not held is nil
 * @param indexedCapitalGains the same parts with a qualifying cost indexed, nil or more, key for key with
 *     {@code capitalGains}
 */
public record HeadsAfterSetOff(Money total, Map<CapitalGainsPart, Money> capitalGains,
        Map<CapitalGainsPart, Money> indexedCapitalGains) implements NodeValue {

    public HeadsAfterSetOff {
        if (total == null || capitalGains == null || indexedCapitalGains == null) {
            throw new IllegalArgumentException("Heads after set-off need the total, the capital-gains parts and their"
                    + " indexed measure");
        }
        if (!capitalGains.keySet().equals(indexedCapitalGains.keySet())) {
            throw new IllegalArgumentException("A part's indexed measure must be carried for exactly the parts held:"
                    + " " + capitalGains.keySet() + " held, " + indexedCapitalGains.keySet() + " measured");
        }
        capitalGains = Map.copyOf(capitalGains);
        indexedCapitalGains = Map.copyOf(indexedCapitalGains);
    }

    /** Heads whose capital-gains parts hold no indexed measure — each part's measure is its gain. */
    public HeadsAfterSetOff(Money total, Map<CapitalGainsPart, Money> capitalGains) {
        this(total, capitalGains, capitalGains);
    }

    /** One capital-gains part after set-off; nil for a part not held. */
    public Money capitalGains(CapitalGainsPart part) {
        return capitalGains.getOrDefault(part, Money.ZERO);
    }

    /** One part's gain with its qualifying cost indexed; nil for a part not held. */
    public Money indexedCapitalGains(CapitalGainsPart part) {
        return indexedCapitalGains.getOrDefault(part, Money.ZERO);
    }

    @Override
    public Money asMoney() {
        return total;
    }
}
