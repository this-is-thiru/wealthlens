package com.thiru.wealthlens.taxengine.rules.component;

import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;

/**
 * A period for which the pack states no withdrawal terms, and why (W-238e1 C2a): the instrument that governed it has no
 * Gazette copy the pack could be read from. Unknown is an answer; a term filled in from a consolidation is not.
 *
 * @param validity the days without stated terms
 * @param reason why, with the backlog row that will state them
 */
public record UnstatedTerms(DateRange validity, String reason) {

    public UnstatedTerms {
        if (validity == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A period without stated terms must say when and why");
        }
    }
}
