package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * The heads once every loss is set off against the others (D-243): their total, and what each capital-gains part still
 * holds — the special-rate parts are taxed on their own basis, so the total alone would lose them.
 *
 * @param total every head's income after set-off, which gross total income is
 * @param capitalGains each capital-gains part held, after other heads' losses, nil or more; a part not held is nil
 */
public record HeadsAfterSetOff(Money total, Map<CapitalGainsPart, Money> capitalGains) implements NodeValue {

    public HeadsAfterSetOff {
        if (total == null || capitalGains == null) {
            throw new IllegalArgumentException("Heads after set-off need the total and the capital-gains parts");
        }
        capitalGains = Map.copyOf(capitalGains);
    }

    /** One capital-gains part after set-off; nil for a part not held. */
    public Money capitalGains(CapitalGainsPart part) {
        return capitalGains.getOrDefault(part, Money.ZERO);
    }

    @Override
    public Money asMoney() {
        return total;
    }
}
