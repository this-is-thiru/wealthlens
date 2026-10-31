package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * The capital-gains head after its own losses are set off (S2 s.108(2); 1961 s.70(2), (3); D-243): what each part
 * still holds, and what loss the head could not absorb.
 *
 * <p>Kept by part, not as one figure, because each part is taxed on its own basis and absorbs other heads' losses in
 * its own turn. A loss left is not set off against any other head (S2 s.109(2); 1961 s.71(3)); it is carried forward
 * (S2 s.111; 1961 s.74; D-247).
 *
 * <p>Alongside each part's gain it carries the gain with the qualifying cost lifted by the Cost Inflation Index, where
 * any bucket of the part qualified (W-088g, D-426). The two maps are kept key for key and lowered together, because the
 * cap in {@code RATE-CG-LONG-OTHER} compares a percentage of one against a percentage of the other: a part whose every
 * bucket failed to qualify carries its gain as both, which leaves the 12.5 per cent limb the lesser.
 *
 * @param parts the surviving gain of each part that holds one, nil or more; a part not held is nil
 * @param indexedParts the same parts with a qualifying cost indexed, nil or more, key for key with {@code parts}
 * @param shortTermLossLeft the year's short-term loss no part absorbed
 * @param longTermLossLeft the year's long-term loss no long-term part absorbed
 */
public record CapitalGainsHead(Map<CapitalGainsPart, Money> parts, Map<CapitalGainsPart, Money> indexedParts,
        Money shortTermLossLeft, Money longTermLossLeft) implements NodeValue {

    public CapitalGainsHead {
        if (parts == null || indexedParts == null || shortTermLossLeft == null || longTermLossLeft == null) {
            throw new IllegalArgumentException("A capital-gains head needs its parts, their indexed measure and the"
                    + " losses left");
        }
        parts.forEach((part, amount) -> {
            if (amount.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A part surviving set-off is nil or more, but " + part + " was "
                        + amount);
            }
        });
        indexedParts.forEach((part, amount) -> {
            if (amount.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A part's indexed measure is nil or more, but " + part + " was "
                        + amount);
            }
        });
        if (!parts.keySet().equals(indexedParts.keySet())) {
            throw new IllegalArgumentException("A part's indexed measure must be carried for exactly the parts held:"
                    + " " + parts.keySet() + " held, " + indexedParts.keySet() + " measured");
        }
        parts = Map.copyOf(parts);
        indexedParts = Map.copyOf(indexedParts);
    }

    /** A head whose parts have no indexed measure — each part's measure is its gain. */
    public CapitalGainsHead(Map<CapitalGainsPart, Money> parts, Money shortTermLossLeft, Money longTermLossLeft) {
        this(parts, parts, shortTermLossLeft, longTermLossLeft);
    }

    /** No capital gains at all. */
    public static CapitalGainsHead none() {
        return new CapitalGainsHead(Map.of(), Money.ZERO, Money.ZERO);
    }

    /** One part's surviving gain; nil for a part the head does not hold. */
    public Money part(CapitalGainsPart part) {
        return parts.getOrDefault(part, Money.ZERO);
    }

    /** One part's gain with its qualifying cost indexed; nil for a part the head does not hold. */
    public Money indexedPart(CapitalGainsPart part) {
        return indexedParts.getOrDefault(part, Money.ZERO);
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
