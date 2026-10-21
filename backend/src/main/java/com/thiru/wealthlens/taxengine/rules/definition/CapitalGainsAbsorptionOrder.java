package com.thiru.wealthlens.taxengine.rules.definition;

import java.util.List;
import java.util.Set;

/**
 * The order in which the parts of the capital-gains head absorb a loss (S2 s.108(2), s.109(1); 1961 ss.70(2), (3),
 * 71(2); D-243).
 *
 * <p>Neither Act orders them, and the order changes the tax: slab gains bear up to the top slab rate, the listed-equity
 * gains less, and the first part of the listed long-term gains nothing. So the order is declared here and named on
 * every set-off line, as {@link AbsorptionOrder} is for the heads. Whether the assessee may choose another is OQ-32.
 *
 * <p>That it names every part {@code CG-PARTS} declares, each once, is checked by the pack validation the build runs
 * ({@code PackValidator}; D-309).
 *
 * @param parts every part, each once, in the order they absorb a loss
 */
public record CapitalGainsAbsorptionOrder(List<CapitalGainsPart> parts) implements RuleDefinition {

    public CapitalGainsAbsorptionOrder {
        if (parts == null) {
            throw new IllegalArgumentException("A capital-gains absorption order needs its parts");
        }
        parts = List.copyOf(parts);
        if (Set.copyOf(parts).size() != parts.size()) {
            throw new IllegalArgumentException("A capital-gains absorption order names every part once, but was "
                    + parts);
        }
    }
}
