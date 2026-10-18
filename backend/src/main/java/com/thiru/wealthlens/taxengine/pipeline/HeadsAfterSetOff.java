package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.EnumMap;
import java.util.Map;

/**
 * The heads once every loss is set off against the others (D-243): their total, and what each capital-gains part still
 * holds — the special-rate parts are taxed on their own basis, so the total alone would lose them.
 *
 * @param total every head's income after set-off, which gross total income is
 * @param capitalGains each capital-gains part after other heads' losses, nil or more
 */
public record HeadsAfterSetOff(Money total, Map<CapitalGainsPart, Money> capitalGains) implements NodeValue {

    public HeadsAfterSetOff {
        if (total == null || capitalGains == null) {
            throw new IllegalArgumentException("Heads after set-off need the total and the capital-gains parts");
        }
        Map<CapitalGainsPart, Money> every = new EnumMap<>(CapitalGainsPart.class);
        for (CapitalGainsPart part : CapitalGainsPart.values()) {
            every.put(part, capitalGains.getOrDefault(part, Money.ZERO));
        }
        capitalGains = Map.copyOf(every);
    }

    /** One capital-gains part after set-off. */
    public Money capitalGains(CapitalGainsPart part) {
        return capitalGains.get(part);
    }

    @Override
    public Money asMoney() {
        return total;
    }
}
