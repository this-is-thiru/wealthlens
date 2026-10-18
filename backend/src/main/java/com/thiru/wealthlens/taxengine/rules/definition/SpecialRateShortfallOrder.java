package com.thiru.wealthlens.taxengine.rules.definition;

import java.util.EnumSet;
import java.util.List;

/**
 * The order in which the special-rate capital-gains parts take up the basic exemption the rest of total income leaves
 * unused (S2 s.196(2), s.197(2), s.198(3); 1961 ss.111A(1), 112(1)(a), 112A(2) provisos; D-244).
 *
 * <p>Each section measures the shortfall against total income "as reduced by such" gains alone, and the sections nest,
 * so neither Act says which gain absorbs it first. The order is declared here and named on the shift's line: for TY
 * 2025-26 it is the order the Department's ITR-2 utility uses.
 *
 * @param parts the three special-rate parts, each once, in the order they absorb the shortfall
 */
public record SpecialRateShortfallOrder(List<CapitalGainsPart> parts) implements RuleDefinition {

    private static final EnumSet<CapitalGainsPart> SPECIAL_RATE_PARTS = EnumSet.of(
            CapitalGainsPart.SHORT_TERM_LISTED_EQUITY, CapitalGainsPart.LONG_TERM_OTHER,
            CapitalGainsPart.LONG_TERM_LISTED_EQUITY);

    public SpecialRateShortfallOrder {
        if (parts == null) {
            throw new IllegalArgumentException("A special-rate shortfall order needs its parts");
        }
        parts = List.copyOf(parts);
        if (parts.size() != SPECIAL_RATE_PARTS.size() || !EnumSet.copyOf(parts).equals(SPECIAL_RATE_PARTS)) {
            throw new IllegalArgumentException("A special-rate shortfall order names each special-rate part once, and"
                    + " only those, but was " + parts);
        }
    }
}
