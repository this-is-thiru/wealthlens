package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * The interest a recognised provident fund credited in the tax year to the employee's balance, and the rate it was
 * allowed at (S2 Sch. XI Pt A para 6 as substituted by the Finance Act, 2026; 1961 Sch. IV Pt A r.6(b); D-151). Only
 * the part allowed above the notified rate is salary; both figures are on the fund's annual statement, so they are
 * entered rather than derived.
 *
 * @param employment which entered employment maintains the fund, by position
 * @param interestCredited the interest credited on the balance in the tax year
 * @param rateAllowed the annual rate the fund allowed it at
 */
public record RecognisedFundInterest(int employment, Money interestCredited, Rate rateAllowed) {

    public RecognisedFundInterest {
        if (interestCredited == null || rateAllowed == null) {
            throw new IllegalArgumentException("A fund's interest needs the amount credited and the rate allowed");
        }
        if (employment < 0 || interestCredited.isLessThan(Money.ZERO) || rateAllowed.compareTo(Rate.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "A fund's interest position, amount and rate may not be negative");
        }
    }
}
