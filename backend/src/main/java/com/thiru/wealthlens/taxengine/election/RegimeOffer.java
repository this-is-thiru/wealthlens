package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;

/**
 * Whether a regime is open to this assessee for the year, and why not where it is not ({@code DR 17} L3; D-222).
 *
 * <p>An offer rather than a filtered list: a barred regime with a stated reason is an answer, a missing one is not.
 * {@code DR 17} L3 also has continuing and lapsed offers; they turn on the s.202(4)(a) binding and the election
 * history, and arrive with W-082e (WI rule B2).
 */
public sealed interface RegimeOffer {

    /** The regime offered or barred. */
    RegimeCode code();

    /** The assessee may compute under the regime. */
    record Available(RegimeSpec regime) implements RegimeOffer {

        public Available {
            if (regime == null) {
                throw new IllegalArgumentException("An available offer needs its regime");
            }
        }

        @Override
        public RegimeCode code() {
            return regime.code();
        }
    }

    /**
     * The assessee may not.
     *
     * @param code the regime
     * @param reason why, in words a person can act on
     */
    record Barred(RegimeCode code, String reason) implements RegimeOffer {

        public Barred {
            if (code == null || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("A barred offer needs its regime and the reason");
            }
        }
    }
}
