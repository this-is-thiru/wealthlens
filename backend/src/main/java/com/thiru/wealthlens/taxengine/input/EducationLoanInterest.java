package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * Interest paid in the tax year on a loan for higher education — the assessee's, a spouse's, a child's, or a student's
 * the assessee is guardian of (S2 s.129; D-139). The lender (a financial institution or approved charitable
 * institution) and payment out of taxable income are conditions of entry.
 *
 * @param interestPaid the interest paid in the tax year
 * @param initialTaxYear the tax year in which the assessee started paying the interest (s.129(3)(d))
 */
public record EducationLoanInterest(Money interestPaid, TaxYearId initialTaxYear) {

    public EducationLoanInterest {
        if (interestPaid == null || initialTaxYear == null) {
            throw new IllegalArgumentException("Education-loan interest needs the interest paid and the initial year");
        }
        if (interestPaid.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Education-loan interest may not be negative: " + interestPaid);
        }
    }
}
