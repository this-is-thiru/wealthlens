package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The facts the accretion on the employer's retirement contributions beyond the limit is computed from (S2
 * s.17(1)(i), S4 r.16; 1961 s.17(2)(viia), Rules 1962 r.3B; D-127). The funds are the three of the aggregate
 * (S2 s.17(1)(h)) taken together; earlier years' figures and the funds' own figures are history the taxpayer enters,
 * not something the engine can derive. The year's own excess is the engine's (W-073a).
 *
 * @param earlierExcessContributions PC1 — the employer's contributions beyond the limit in the tax years from 1 April
 *     2020 other than this one
 * @param earlierTaxedAccretions TP1 — the accretion taxed in those years
 * @param incomeAccrued I — the income accrued in the year in the employee's accounts in the funds
 * @param openingBalance the balance to the credit of those accounts on the first day of the tax year
 * @param closingBalance the balance to the credit of those accounts on the last day of the tax year
 */
public record RetirementFundHistory(
        Money earlierExcessContributions,
        Money earlierTaxedAccretions,
        Money incomeAccrued,
        Money openingBalance,
        Money closingBalance) {

    public RetirementFundHistory {
        if (earlierExcessContributions == null || earlierTaxedAccretions == null || incomeAccrued == null
                || openingBalance == null || closingBalance == null) {
            throw new IllegalArgumentException("A retirement fund history needs every figure; enter 0 where nil");
        }
        for (Money figure : new Money[] {earlierExcessContributions, earlierTaxedAccretions, incomeAccrued,
                openingBalance, closingBalance}) {
            if (figure.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A retirement fund history's figures may not be negative");
            }
        }
    }
}
