package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The fee for a return furnished after its due date (2025 s.428(a), as substituted by the Finance Act, 2026 s.96; 1961
 * s.234F(1), as substituted by the Finance Act, 2021 s.60; W-112): a lower fee where total income does not exceed an amount,
 * the fee otherwise. A fee, not interest: it does not grow with the delay.
 *
 * @param incomeAtMost the total income up to which the lower fee applies — "does not exceed", so equal is lower
 * @param lowerFee the lower fee
 * @param lowerFeeIsCeiling whether the lower fee is a ceiling ("shall not exceed", 1961) rather than the fee itself (2025)
 * @param fee the fee in any other case
 */
public record LateReturnFee(Money incomeAtMost, Money lowerFee, boolean lowerFeeIsCeiling, Money fee) implements RuleDefinition {

    public LateReturnFee {
        if (incomeAtMost == null || lowerFee == null || fee == null || !lowerFee.isGreaterThan(Money.ZERO)
                || !fee.isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("A late-return fee needs an income and two fees above nil, but was " + incomeAtMost
                    + ", " + lowerFee + " and " + fee);
        }
    }

    /** The fee on a total income of {@code totalIncome}; a ceiling is shown at its maximum. */
    public Money feeFor(Money totalIncome) {
        return totalIncome.isLessThanOrEqual(incomeAtMost) ? lowerFee : fee;
    }

    /** Whether the fee on {@code totalIncome} is only an upper limit, which the trace must say. */
    public boolean isCeilingFor(Money totalIncome) {
        return lowerFeeIsCeiling && totalIncome.isLessThanOrEqual(incomeAtMost);
    }
}
