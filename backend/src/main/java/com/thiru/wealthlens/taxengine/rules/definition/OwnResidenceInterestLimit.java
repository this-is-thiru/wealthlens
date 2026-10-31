package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The limits on interest deducted for the houses whose annual value is nil, and the test that earns the higher one
 * (S2 s.22(2), (5) as amended by FA 2026 s.38; 1961 s.24(b) provisos; D-156). A definition carrying its limits, as
 * {@link SanctionedLoanLimit} does, because the test is dates and years and a rule version is a cap or a definition,
 * never both.
 *
 * <p>Two groups and one ceiling: the interest on houses failing the test is limited, together, to
 * {@code anyOtherCase}; the interest on houses passing it, together, to {@code conditionsMet}; and both together to
 * {@code aggregate} — 1961's first, second and last provisos, which S2 s.22(2) and (5) restate.
 *
 * @param conditionsMet the limit where the house was acquired or constructed with the capital, in time, and the
 *     lender's certificate is furnished
 * @param anyOtherCase the limit in any other case
 * @param aggregate the limit on both groups together
 * @param completionYearsAfterBorrowingYear within how many years from the end of the tax year the capital was
 *     borrowed in the acquisition or construction must be completed
 * @param borrowedOnOrAfter the earliest date the capital may have been borrowed on, where the Act sets one
 */
public record OwnResidenceInterestLimit(
        Money conditionsMet,
        Money anyOtherCase,
        Money aggregate,
        int completionYearsAfterBorrowingYear,
        Optional<LocalDate> borrowedOnOrAfter) implements RuleDefinition {

    public OwnResidenceInterestLimit {
        if (conditionsMet == null || anyOtherCase == null || aggregate == null || borrowedOnOrAfter == null) {
            throw new IllegalArgumentException("An own-residence interest limit needs its three limits;"
                    + " Optional.empty() where the Act sets no earliest borrowing date");
        }
        if (completionYearsAfterBorrowingYear < 0) {
            throw new IllegalArgumentException("The years to complete a house may not be negative, but were "
                    + completionYearsAfterBorrowingYear);
        }
    }
}
