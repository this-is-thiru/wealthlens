package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Map;

/**
 * The heads once every loss is set off against the others (D-243): their total, and what each capital-gains part still
 * holds — the special-rate parts are taxed on their own basis, so the total alone would lose them.
 *
 * <p>Beside a part it carries the land or building gains the cap on their tax applies to, as {@link CapitalGainsHead}
 * carries them ({@link IndexedGain}; W-088g, D-426; W-088g3, D-431), lowered in proportion whenever a loss is absorbed.
 *
 * @param total every head's income after set-off, which gross total income is
 * @param capitalGains each capital-gains part held, after other heads' losses, nil or more; a part not held is nil
 * @param indexedGains each held part's qualifying gains, together no more than the part
 */
public record HeadsAfterSetOff(Money total, Map<CapitalGainsPart, Money> capitalGains,
        Map<CapitalGainsPart, List<IndexedGain>> indexedGains) implements NodeValue {

    public HeadsAfterSetOff {
        if (total == null || capitalGains == null || indexedGains == null) {
            throw new IllegalArgumentException("Heads after set-off need the total, the capital-gains parts and their"
                    + " indexed measure");
        }
        IndexedGain.requireHeld(capitalGains, indexedGains);
        capitalGains = Map.copyOf(capitalGains);
        indexedGains = IndexedGain.copied(indexedGains);
    }

    /** Heads none of whose capital gains the cap on land or building reaches. */
    public HeadsAfterSetOff(Money total, Map<CapitalGainsPart, Money> capitalGains) {
        this(total, capitalGains, Map.of());
    }

    /** One capital-gains part after set-off; nil for a part not held. */
    public Money capitalGains(CapitalGainsPart part) {
        return capitalGains.getOrDefault(part, Money.ZERO);
    }

    /** One part's gains the cap on land or building reaches; none for a part without one. */
    public List<IndexedGain> indexedGains(CapitalGainsPart part) {
        return indexedGains.getOrDefault(part, List.of());
    }

    @Override
    public Money asMoney() {
        return total;
    }
}
