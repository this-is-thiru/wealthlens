package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * What an employer contributed in the tax year to one of the employee's retirement funds (S2 s.17(1)(h); 1961
 * s.17(2)(vii); D-126). The contributions are not salary; only their aggregate beyond the limit is.
 *
 * @param employment which entered employment contributed, by position
 * @param fund the fund it went to
 * @param amount the amount contributed in the tax year
 */
public record EmployerFundContribution(int employment, EmployerFund fund, Money amount) {

    public EmployerFundContribution {
        if (fund == null || amount == null) {
            throw new IllegalArgumentException("An employer contribution needs its fund and amount");
        }
        if (employment < 0 || amount.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("An employer contribution's position and amount may not be negative");
        }
    }
}
