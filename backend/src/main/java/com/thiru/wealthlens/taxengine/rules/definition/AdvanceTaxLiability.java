package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * Who must pay advance tax (2025 s.403(3), s.404; 1961 s.207(2), s.208; W-089c): a resident individual of the stated age at
 * any time during the year with no business or profession income is not liable, and nobody is liable whose tax for the year,
 * less tax deducted or collected at source, is below the amount. Both figures are the row's, so a change in either is data.
 *
 * @param exemptFromAge the age, reached at any time during the year, from which a resident individual without business
 *     income pays none
 * @param atLeast the year's advance tax at which the liability begins — "₹10000 or more", so equal is liable
 */
public record AdvanceTaxLiability(int exemptFromAge, Money atLeast) implements RuleDefinition {

    public AdvanceTaxLiability {
        if (exemptFromAge <= 0 || atLeast == null || !atLeast.isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("An advance-tax liability needs an age above nil and an amount above nil, but was "
                    + exemptFromAge + " and " + atLeast);
        }
    }

    /** Whether an advance tax of {@code amount} is payable at all: "or more", so the amount itself is. */
    public boolean reaches(Money amount) {
        return !amount.isLessThan(atLeast);
    }
}
