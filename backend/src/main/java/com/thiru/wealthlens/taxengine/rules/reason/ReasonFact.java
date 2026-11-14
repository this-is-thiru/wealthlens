package com.thiru.wealthlens.taxengine.rules.reason;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;

/**
 * One fact a {@link Reason} carries, typed so the renderer can put it into words: a regime through the content's term
 * for it (W-327a, D-678). A kind is added with the first reason that needs it.
 */
public sealed interface ReasonFact {

    /** A regime, named in words by the content's {@code terms.regime}. */
    record RegimeFact(RegimeCode regime) implements ReasonFact {

        public RegimeFact {
            if (regime == null) {
                throw new IllegalArgumentException("A regime fact must name its regime");
            }
        }
    }
}
