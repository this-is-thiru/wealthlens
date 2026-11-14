package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The share of a pension whose commuted value may be deducted when an employer outside the government schemes
 * commutes it (S2 s.19(1) Table Sl. 8; 1961 s.10(10A)(ii); D-103).
 *
 * <p>Not a cap shape: which share applies turns on whether the employee also received gratuity, a fact about the
 * claim, and the Acts state the shares as fractions — "one-third", "one-half" — which a percentage cannot hold
 * exactly. Both are unit fractions, so each is carried as the number it divides by, and the division happens once,
 * in {@link Money#apportioned}.
 *
 * @param oneOverWhereGratuityReceived the divisor where the employee receives gratuity — three for "one-third"
 * @param oneOverOtherwise the divisor in any other case — two for "one-half"
 */
public record CommutedPensionShare(int oneOverWhereGratuityReceived, int oneOverOtherwise) implements RuleDefinition {

    public CommutedPensionShare {
        if (oneOverWhereGratuityReceived < 2 || oneOverOtherwise < 2) {
            throw new IllegalArgumentException("A share of the pension is a fraction, so each divisor is at least two,"
                    + " but was " + oneOverWhereGratuityReceived + " and " + oneOverOtherwise);
        }
    }

    /** The deductible limit: the share of the whole pension's commuted value. */
    public Money limit(Money commutedValueOfWholePension, boolean gratuityReceived) {
        return commutedValueOfWholePension.apportioned(1,
                gratuityReceived ? oneOverWhereGratuityReceived : oneOverOtherwise);
    }
}
