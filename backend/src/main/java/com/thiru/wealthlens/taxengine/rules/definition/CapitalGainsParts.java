package com.thiru.wealthlens.taxengine.rules.definition;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The parts of the capital-gains head, each with the term of the gains it collects and whether a long-term loss may be
 * set off against it (S2 s.108(2)(b), s.111(1)(a)(ii); 1961 s.70(3), s.74(1); D-309).
 *
 * <p>A definition so that the restriction is the row's, versioned by tax year, not a switch on the part: a new part, or
 * a change to which parts a long-term loss reaches, is new data. Which bucket falls in which part is {@code RS-CG-PART}'s
 * (D-289). The term is carried as its code — the rules vocabulary cannot see the engine's input types — as the part rows
 * test {@code capitalgains.term}.
 *
 * @param parts each part once, in the head's order
 */
public record CapitalGainsParts(List<Declared> parts) implements RuleDefinition {

    /**
     * One part as the row declares it.
     *
     * @param part the part
     * @param term the code of the term of the gains it collects; a loss on the part is a loss of that term
     * @param canAbsorbLongTermLoss whether a long-term loss may be set off against the part
     */
    public record Declared(CapitalGainsPart part, String term, boolean canAbsorbLongTermLoss) {

        public Declared {
            if (part == null || term == null || term.isBlank()) {
                throw new IllegalArgumentException("A declared capital-gains part needs its part and its term");
            }
        }
    }

    public CapitalGainsParts {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("The capital-gains parts need at least one part");
        }
        parts = List.copyOf(parts);
        Set<CapitalGainsPart> seen = new HashSet<>();
        for (Declared declared : parts) {
            if (!seen.add(declared.part())) {
                throw new IllegalArgumentException("The capital-gains parts name " + declared.part() + " twice");
            }
        }
    }

    /** The part as declared; empty if the row does not declare it. */
    public Optional<Declared> of(CapitalGainsPart part) {
        return parts.stream().filter(declared -> declared.part().equals(part)).findFirst();
    }
}
