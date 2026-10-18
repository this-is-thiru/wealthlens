package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import java.util.HashSet;
import java.util.List;

/**
 * The order in which the other heads absorb a head's loss (S2 s.109(1); 1961 s.71(1), (2); D-217).
 *
 * <p>Neither Act prescribes one, and with special-rate income the order changes the surcharge ({@code DR 13} R-7), so
 * the order is declared here rather than left to iteration order, and every set-off line names it. The loss head
 * itself is skipped. The assessee may choose another (D-221): this is the order used when none is elected.
 *
 * @param heads the heads in the order they absorb a loss, each once
 */
public record AbsorptionOrder(List<HeadCode> heads) implements RuleDefinition {

    public AbsorptionOrder {
        if (heads == null || heads.isEmpty()) {
            throw new IllegalArgumentException("An absorption order needs at least one head");
        }
        heads = List.copyOf(heads);
        if (new HashSet<>(heads).size() != heads.size()) {
            throw new IllegalArgumentException("An absorption order names each head once, but was " + heads);
        }
    }
}
