package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;

/**
 * A deduction's run of tax years: the initial year and a number of years immediately succeeding it — s.129(2)'s
 * "the initial tax year and seven tax years immediately succeeding the initial tax year" (S2; D-139). A definition
 * because it is years, not rupees.
 *
 * @param succeedingYears how many tax years after the initial one the deduction runs
 */
public record TaxYearSpan(int succeedingYears) implements RuleDefinition {

    public TaxYearSpan {
        if (succeedingYears < 0) {
            throw new IllegalArgumentException("A span of tax years may not be negative, but was " + succeedingYears);
        }
    }

    /** Whether {@code taxYear} falls in the span that begins with {@code initial}. */
    public boolean holds(TaxYearId initial, TaxYearId taxYear) {
        int after = taxYear.startYear() - initial.startYear();
        return after >= 0 && after <= succeedingYears;
    }
}
