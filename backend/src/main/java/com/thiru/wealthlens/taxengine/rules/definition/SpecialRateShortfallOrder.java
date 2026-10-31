package com.thiru.wealthlens.taxengine.rules.definition;

import java.util.List;
import java.util.Set;

/**
 * The order in which the special-rate capital-gains parts take up the basic exemption the rest of total income leaves
 * unused (S2 s.196(2), s.197(2), s.198(3); 1961 ss.111A(1), 112(1)(a), 112A(2) provisos; D-244).
 *
 * <p>Each section measures the shortfall against total income "as reduced by such" gains alone, and the sections nest,
 * so neither Act says which gain absorbs it first. The order is declared here and named on the shift's line: for TY
 * 2025-26 it is the order the Department's ITR-2 utility uses.
 *
 * <p>That it names exactly the parts a special-rate row charges, each once, is checked by the pack validation the build
 * runs ({@code PackValidator}; D-309).
 *
 * @param parts the special-rate parts, each once, in the order they absorb the shortfall
 */
public record SpecialRateShortfallOrder(List<CapitalGainsPart> parts) implements RuleDefinition {

    public SpecialRateShortfallOrder {
        if (parts == null) {
            throw new IllegalArgumentException("A special-rate shortfall order needs its parts");
        }
        parts = List.copyOf(parts);
        if (Set.copyOf(parts).size() != parts.size()) {
            throw new IllegalArgumentException("A special-rate shortfall order names each special-rate part once, but"
                    + " was " + parts);
        }
    }
}
