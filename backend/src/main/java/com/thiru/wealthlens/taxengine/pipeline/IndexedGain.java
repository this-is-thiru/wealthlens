package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One land or building gain the cap on its tax applies to, with the same gain computed on indexed cost (1961
 * s.112(1)(a) second proviso; 2025 s.197(3); W-088g, D-426) — the two measures {@code RATE-CG-LONG-OTHER} compares.
 *
 * <p>Kept per qualifying bucket, beside its part, because the cap is measured on the land gain alone (OQ-36 as
 * re-decided, W-088g3 C1a, D-431): the rest of the part is charged its rate without it. Whatever lowers a part — a
 * losing bucket netted into it, a loss set off against it, the shift of the nil band — lowers each of its gains in
 * proportion, and the indexed measure by the same rupees (W-088g3 C3a; OQ-37 C3a).
 *
 * @param gain the land gain still held in the part, nil or more
 * @param indexedGain the same gain computed on indexed cost, lowered by the same rupees, nil or more
 */
public record IndexedGain(Money gain, Money indexedGain) {

    public IndexedGain {
        if (gain == null || indexedGain == null) {
            throw new IllegalArgumentException("An indexed gain needs the gain and its indexed measure");
        }
        if (gain.isLessThan(Money.ZERO) || indexedGain.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("An indexed gain's measures are nil or more, but were " + gain + " and "
                    + indexedGain);
        }
    }

    /**
     * The gains of a part lowered from {@code from} to {@code to}: each in proportion, its indexed measure by the same
     * rupees, nil rather than negative. Where rounding would leave the gains a paisa above {@code to}, the last gives it
     * up, so they never hold more than the part; a part lowered to nil keeps none.
     *
     * @param gains the part's gains, together no more than {@code from}
     * @param from the part before the reduction, more than nil where {@code gains} hold any
     * @param to the part after it, from nil to {@code from}
     */
    public static List<IndexedGain> lowered(List<IndexedGain> gains, Money from, Money to) {
        if (gains.isEmpty() || !to.isLessThan(from)) {
            return List.copyOf(gains);
        }
        if (!to.isGreaterThan(Money.ZERO)) {
            return List.of();
        }
        List<IndexedGain> lowered = new ArrayList<>();
        Money held = Money.ZERO;
        for (IndexedGain gain : gains) {
            Money scaled = gain.gain().scaledBy(to, from);
            lowered.add(gain.loweredTo(scaled));
            held = held.plus(scaled);
        }
        if (held.isGreaterThan(to)) {
            IndexedGain last = lowered.removeLast();
            lowered.add(last.loweredTo(last.gain().minus(held.minus(to))));
        }
        return List.copyOf(lowered);
    }

    /** The sum of {@code gains}' gains. */
    public static Money totalOf(List<IndexedGain> gains) {
        return gains.stream().map(IndexedGain::gain).reduce(Money.ZERO, Money::plus);
    }

    /** Refuses lists for a part not held, and a part's gains that together exceed it — shared by the two carriers. */
    static void requireHeld(Map<CapitalGainsPart, Money> parts, Map<CapitalGainsPart, List<IndexedGain>> gains) {
        gains.forEach((part, listed) -> {
            Money held = parts.getOrDefault(part, Money.ZERO);
            if (!parts.containsKey(part) || totalOf(listed).isGreaterThan(held)) {
                throw new IllegalArgumentException("A part's indexed gains must lie within a held part, but " + part
                        + " holds " + held + " and lists " + totalOf(listed));
            }
        });
    }

    /** An immutable copy, every list copied too. */
    static Map<CapitalGainsPart, List<IndexedGain>> copied(Map<CapitalGainsPart, List<IndexedGain>> gains) {
        Map<CapitalGainsPart, List<IndexedGain>> copy = new HashMap<>();
        gains.forEach((part, listed) -> copy.put(part, List.copyOf(listed)));
        return Map.copyOf(copy);
    }

    private IndexedGain loweredTo(Money lower) {
        return new IndexedGain(lower, indexedGain.minus(gain.minus(lower)).floorAtZero());
    }
}
