package com.thiru.wealthlens.taxengine.rules.regime;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;

/**
 * What a taxpayer may do after leaving a regime.
 *
 * <p>The third branch is the one that makes this a sealed family rather than a boolean.
 * s.202(4)(a)(iii) says the option may be withdrawn <em>&ldquo;only once&rdquo;</em> and thereafter
 * the person is <em>&ldquo;never eligible&rdquo;</em> — <b>except</b>, per (iv), <em>&ldquo;where
 * such person ceases to have any income from business or profession&rdquo;</em>, at which point a
 * different election route opens.
 *
 * <p>So &ldquo;permanently ineligible&rdquo; is not permanent, and the escape is a condition on a
 * fact that can change years later. Modelled as a boolean, that taxpayer is told they are barred
 * for life from a regime they are entitled to.
 */
public sealed interface AfterWithdrawal {

    /** The taxpayer may elect in again. */
    record MayReElect() implements AfterWithdrawal {}

    /** Barred, with no stated escape. */
    record PermanentlyIneligible() implements AfterWithdrawal {}

    /**
     * Barred unless a condition later holds, which reopens a different route — s.202(4)(a)(iv).
     *
     * @param revival what must become true; for s.202(4)(a)(iv), that the taxpayer no longer has
     *     income under the head business or profession
     * @param thenUse the route that opens when it does
     */
    record IneligibleUnless(Condition revival, ElectionRoute thenUse) implements AfterWithdrawal {

        public IneligibleUnless {
            if (revival == null) {
                throw new IllegalArgumentException(
                        "IneligibleUnless must state what revives eligibility; without it this is"
                                + " PermanentlyIneligible wearing a different name");
            }
            if (thenUse == null) {
                throw new IllegalArgumentException(
                        "IneligibleUnless must name the route that opens; a revival with nowhere"
                                + " to go tells a taxpayer they qualify and not how to act on it");
            }
        }
    }
}
