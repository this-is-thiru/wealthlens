package com.thiru.wealthlens.taxengine.input;

/**
 * What was already claimed in one four-calendar-year block of the leave travel concession, in earlier tax years
 * (S4 r.278(2)–(4); Rules 1962 r.2B(2)–(3); D-100). Consumed, never remaining: what is left is the engine's
 * subtraction.
 *
 * @param journeysClaimed journeys already counted against the block's two
 * @param carriedJourneyClaimed whether the journey carried over from the previous, unused block — which the Rules
 *     do not count against the two — was already claimed in this block's first calendar year
 */
public record LeaveTravelBlock(int journeysClaimed, boolean carriedJourneyClaimed) {

    public LeaveTravelBlock {
        if (journeysClaimed < 0) {
            throw new IllegalArgumentException(
                    "A block's journeys claimed must not be negative, but was " + journeysClaimed);
        }
    }
}
