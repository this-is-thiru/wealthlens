package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Map;

/**
 * The capital-gains head after its own losses are set off (S2 s.108(2); 1961 s.70(2), (3); D-243): what each part
 * still holds, and what loss the head could not absorb.
 *
 * <p>Kept by part, not as one figure, because each part is taxed on its own basis and absorbs other heads' losses in
 * its own turn. A loss left is not set off against any other head (S2 s.109(2); 1961 s.71(3)); it is carried forward
 * (S2 s.111; 1961 s.74; D-247).
 *
 * <p>Beside a part it carries each land or building gain of that part the cap on its tax applies to, with the same gain
 * on indexed cost ({@link IndexedGain}; W-088g, D-426), because {@code RATE-CG-LONG-OTHER} compares the two measures on
 * the land gain alone and charges the rest of the part without them (W-088g3, D-431). A part none of whose buckets
 * qualified carries no list.
 *
 * @param parts the surviving gain of each part that holds one, nil or more; a part not held is nil
 * @param indexedGains each held part's qualifying gains, together no more than the part
 * @param shortTermLossLeft the year's short-term loss no part absorbed
 * @param longTermLossLeft the year's long-term loss no long-term part absorbed
 */
public record CapitalGainsHead(Map<CapitalGainsPart, Money> parts, Map<CapitalGainsPart, List<IndexedGain>> indexedGains,
        Money shortTermLossLeft, Money longTermLossLeft) implements NodeValue {

    public CapitalGainsHead {
        if (parts == null || indexedGains == null || shortTermLossLeft == null || longTermLossLeft == null) {
            throw new IllegalArgumentException("A capital-gains head needs its parts, their indexed measure and the"
                    + " losses left");
        }
        parts.forEach((part, amount) -> {
            if (amount.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A part surviving set-off is nil or more, but " + part + " was "
                        + amount);
            }
        });
        IndexedGain.requireHeld(parts, indexedGains);
        parts = Map.copyOf(parts);
        indexedGains = IndexedGain.copied(indexedGains);
    }

    /** A head none of whose gains the cap on land or building reaches. */
    public CapitalGainsHead(Map<CapitalGainsPart, Money> parts, Money shortTermLossLeft, Money longTermLossLeft) {
        this(parts, Map.of(), shortTermLossLeft, longTermLossLeft);
    }

    /** No capital gains at all. */
    public static CapitalGainsHead none() {
        return new CapitalGainsHead(Map.of(), Money.ZERO, Money.ZERO);
    }

    /** One part's surviving gain; nil for a part the head does not hold. */
    public Money part(CapitalGainsPart part) {
        return parts.getOrDefault(part, Money.ZERO);
    }

    /** One part's gains the cap on land or building reaches; none for a part without one. */
    public List<IndexedGain> indexedGains(CapitalGainsPart part) {
        return indexedGains.getOrDefault(part, List.of());
    }

    /** The head's income: every part's surviving gain. */
    public Money total() {
        return parts.values().stream().reduce(Money.ZERO, Money::plus);
    }

    @Override
    public Money asMoney() {
        return total();
    }
}
