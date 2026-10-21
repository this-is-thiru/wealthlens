package com.thiru.wealthlens.taxengine.rules.rounding;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * How a rule version rounds an amount — s.516's "rounded off to the nearest multiple of ₹10 ignoring any part of a
 * rupee consisting of paise and thereafter … increased to the next higher amount which is a multiple of ten, if the
 * last figure in that amount is five or more" (S2 s.516; 1961 ss.288A, 288B; D-076, D-316).
 *
 * <p>Both elements are the row's, so a changed step or round-up point is new data. "Ignoring … paise" needs no element
 * of its own: with a round-up point in whole rupees, dropping the paise first never moves a remainder across it (4.99
 * and 4 are both below 5), so the method is the same either way — which is why the round-up point must be whole rupees.
 *
 * @param toNearest the multiple an amount is rounded to; a positive whole number of rupees
 * @param roundsUpFrom the remainder from which an amount goes up to the next multiple; a positive whole number of rupees,
 *     at most {@code toNearest}
 */
public record RuleRounding(Money toNearest, Money roundsUpFrom) {

    public RuleRounding {
        if (toNearest == null || roundsUpFrom == null) {
            throw new IllegalArgumentException("A rule rounding needs the multiple it rounds to and the remainder from"
                    + " which it rounds up");
        }
        if (!toNearest.isGreaterThan(Money.ZERO) || !toNearest.isWholeRupees()) {
            throw new IllegalArgumentException(
                    "A rule rounding's step must be a positive whole number of rupees, but was " + toNearest);
        }
        if (!roundsUpFrom.isGreaterThan(Money.ZERO) || !roundsUpFrom.isWholeRupees()
                || roundsUpFrom.isGreaterThan(toNearest)) {
            throw new IllegalArgumentException("A rule rounding's round-up point must be a positive whole number of"
                    + " rupees no more than its step " + toNearest + ", but was " + roundsUpFrom);
        }
    }
}
