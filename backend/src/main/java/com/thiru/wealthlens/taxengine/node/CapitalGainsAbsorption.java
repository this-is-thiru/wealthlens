package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.IndexedGain;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsAbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The capital-gains parts absorbing a loss, in {@code SETOFF-WITHIN-CAPITAL-GAINS}'s declared order (S2 s.109(1),
 * s.111(1)(a); 1961 s.71(2), s.74(1); D-243, D-247). Kept in one place so every loss set off against the head takes
 * the parts the same way, whatever text its lines carry (D-263).
 *
 * <p>Beside each part are the land or building gains the cap on their tax applies to ({@link IndexedGain}; W-088g,
 * D-426): what a loss takes from the part it takes from each of them in proportion, and from their indexed measure by
 * the same rupees, nil rather than below it (W-088g3 C3a, D-431).
 */
final class CapitalGainsAbsorption {

    /** Writes the SET_OFF line for one part's share of a loss, in the caller's words. */
    @FunctionalInterface
    interface PartLine {
        TraceLine of(int sequence, CapitalGainsPart part, Money available, Money taken);
    }

    private final CapitalGainsAbsorptionOrder order;
    private final Map<CapitalGainsPart, Money> parts;
    private final Map<CapitalGainsPart, List<IndexedGain>> indexedGains;

    /** Over {@code parts} and their qualifying gains, both lowered in place as a loss is absorbed. */
    CapitalGainsAbsorption(CapitalGainsAbsorptionOrder order, Map<CapitalGainsPart, Money> parts,
            Map<CapitalGainsPart, List<IndexedGain>> indexedGains) {
        this.order = order;
        this.parts = parts;
        this.indexedGains = indexedGains;
    }

    /** The declared order, which a caller's lines name. */
    CapitalGainsAbsorptionOrder order() {
        return order;
    }

    /**
     * The parts {@code reaches} accepts absorb up to {@code loss}, in order, a line each; returns how much they took.
     */
    Money absorb(Money loss, Predicate<CapitalGainsPart> reaches, List<TraceLine> lines, PartLine line) {
        Money remaining = loss;
        for (CapitalGainsPart part : order.parts()) {
            Money available = parts.getOrDefault(part, Money.ZERO);
            if (!remaining.isGreaterThan(Money.ZERO) || !available.isGreaterThan(Money.ZERO) || !reaches.test(part)) {
                continue;
            }
            Money taken = Money.min(remaining, available);
            parts.put(part, available.minus(taken));
            indexedGains.computeIfPresent(part, (held, gains) -> IndexedGain.lowered(gains, available,
                    available.minus(taken)));
            remaining = remaining.minus(taken);
            lines.add(line.of(lines.size() + 1, part, available, taken));
        }
        return loss.minus(remaining);
    }
}
