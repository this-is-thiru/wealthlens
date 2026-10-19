package com.thiru.wealthlens.taxengine.node;

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
 */
final class CapitalGainsAbsorption {

    /** Writes the SET_OFF line for one part's share of a loss, in the caller's words. */
    @FunctionalInterface
    interface PartLine {
        TraceLine of(int sequence, CapitalGainsPart part, Money available, Money taken);
    }

    private final CapitalGainsAbsorptionOrder order;
    private final Map<CapitalGainsPart, Money> parts;

    /** Over {@code parts}, which are lowered in place as a loss is absorbed. */
    CapitalGainsAbsorption(CapitalGainsAbsorptionOrder order, Map<CapitalGainsPart, Money> parts) {
        this.order = order;
        this.parts = parts;
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
            remaining = remaining.minus(taken);
            lines.add(line.of(lines.size() + 1, part, available, taken));
        }
        return loss.minus(remaining);
    }
}
