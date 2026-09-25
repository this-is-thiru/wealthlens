package com.thiru.wealthlens.taxengine.rules.rounding;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The step a rule version rounds an amount to — "the nearest multiple of ₹10" (S2 s.516; 1961
 * ss.288A, 288B).
 *
 * <p>Stated as data so a rounding row carries what it says rather than loading empty (D-076). Only a
 * step that cannot be a rounding at all is refused here; which steps the engine can apply is the
 * rounding node's to decide, since the Acts state one method and only that one is implemented.
 *
 * @param toNearest the multiple an amount is rounded to; a positive whole number of rupees
 */
public record RuleRounding(Money toNearest) {

    public RuleRounding {
        if (toNearest == null) {
            throw new IllegalArgumentException("A rule rounding needs the multiple it rounds to");
        }
        if (!toNearest.isGreaterThan(Money.ZERO) || !toNearest.isWholeRupees()) {
            throw new IllegalArgumentException(
                    "A rule rounding's step must be a positive whole number of rupees, but was " + toNearest);
        }
    }
}
