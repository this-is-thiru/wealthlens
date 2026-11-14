package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * How interest an assessee pays is calculated (Rules 2026 r.269(b), (c); Rules 1962 r.119A; W-112): the amount it is
 * calculated on rounded down to a multiple, and a fraction of a month counted as a whole month or ignored.
 *
 * @param baseRoundedDownTo the multiple the amount is rounded down to — "any fraction of ₹ 100 shall be ignored"
 * @param fractionOfMonthCountsWhole whether a fraction of a month is a full month (r.269(b)) or ignored (r.269(a))
 */
public record InterestCalculation(Money baseRoundedDownTo, boolean fractionOfMonthCountsWhole) implements RuleDefinition {

    public InterestCalculation {
        if (baseRoundedDownTo == null || !baseRoundedDownTo.isGreaterThan(Money.ZERO) || !baseRoundedDownTo.isWholeRupees()) {
            throw new IllegalArgumentException("Interest needs a whole number of rupees above nil to round its amount to, but was "
                    + baseRoundedDownTo);
        }
    }

    /** The amount interest is calculated on: nil or more, rounded down to the multiple. */
    public Money base(Money amount) {
        return amount.floorAtZero().roundToMultiple(baseRoundedDownTo, baseRoundedDownTo);
    }
}
