package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;

/**
 * The amount is the total of one head's entered lines — "salary includes wages; annuity or pension; …" (S2
 * s.16) read against the registry, which places each component under its head (D-089, D-091).
 *
 * @param head the head whose lines are totalled
 */
public record HeadTotal(HeadCode head) implements RuleDefinition {

    public HeadTotal {
        if (head == null) {
            throw new IllegalArgumentException("A head total needs its head");
        }
    }
}
