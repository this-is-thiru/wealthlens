package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.List;
import java.util.Optional;

/**
 * An interest-free or concessional loan from the employer, to the employee or a member of the household (S4 r.15(5)
 * Table IV Sl. 1; Rules 1962 r.3(7)(i); D-118).
 *
 * <p>The perquisite is the interest the State Bank of India would have charged for a loan for the same purpose, at
 * its rate on the first day of the tax year, on each month-end balance — the "maximum outstanding monthly balance" as
 * the Rules define it — less the interest paid. The Bank's rate is its published figure, not law, so it is entered
 * (D-116 C3). Whether the loan is for treatment of a disease the year's rule lists (rule 18; Rules 1962 r.3A) is a
 * fact the taxpayer states; the engine does not hold the list.
 *
 * @param employment which entered employment lent it, by position
 * @param amountLent the amount lent — the petty-loan test adds these up per employer (D-118 C3a)
 * @param stateBankRate the Bank's annual rate for a loan for the same purpose on the first day of the tax year
 * @param monthEndBalances the balance on the last day of each month in the tax year it was outstanding, at most 12
 * @param interestPaid the interest the employee or the household member paid in the year
 * @param forListedDisease whether it was made for treatment of a disease the year's rule lists
 * @param insuranceReimbursement for such a loan, what a medical insurance scheme reimbursed and after which month
 */
public record EmployerLoan(
        int employment,
        Money amountLent,
        Rate stateBankRate,
        List<Money> monthEndBalances,
        Money interestPaid,
        boolean forListedDisease,
        Optional<InsuranceReimbursement> insuranceReimbursement) {

    /**
     * What a medical insurance scheme reimbursed, which loses the medical nil (Table IV Sl. 1 col. D (b)).
     *
     * @param amount the amount reimbursed
     * @param afterMonth the position in {@code monthEndBalances} of the month it was reimbursed in; the months after
     *     it are valued on the lesser of the balance and the amount
     */
    public record InsuranceReimbursement(Money amount, int afterMonth) {

        public InsuranceReimbursement {
            if (amount == null || amount.isLessThan(Money.ZERO) || afterMonth < 0) {
                throw new IllegalArgumentException("A reimbursement needs an amount of nil or more and a month");
            }
        }
    }

    public EmployerLoan {
        if (amountLent == null || stateBankRate == null || monthEndBalances == null || interestPaid == null
                || insuranceReimbursement == null) {
            throw new IllegalArgumentException("A loan needs the amount lent, the Bank's rate, the balances and the"
                    + " interest paid; pass Optional.empty() where nothing was reimbursed");
        }
        if (employment < 0) {
            throw new IllegalArgumentException("An employment position may not be negative, but was " + employment);
        }
        if (monthEndBalances.size() > 12) {
            throw new IllegalArgumentException("A tax year has twelve month-ends, but " + monthEndBalances.size()
                    + " balances were entered");
        }
        requireAmounts(amountLent, stateBankRate, monthEndBalances, interestPaid);
        if (insuranceReimbursement.isPresent() && !forListedDisease) {
            throw new IllegalArgumentException("An insurance reimbursement matters only for a loan for a listed"
                    + " disease (Table IV Sl. 1 col. D (b)); this loan is not stated as one");
        }
        monthEndBalances = List.copyOf(monthEndBalances);
    }

    private static void requireAmounts(Money amountLent, Rate stateBankRate, List<Money> monthEndBalances,
            Money interestPaid) {
        if (amountLent.isLessThan(Money.ZERO) || interestPaid.isLessThan(Money.ZERO)
                || monthEndBalances.stream().anyMatch(balance -> balance == null || balance.isLessThan(Money.ZERO))) {
            throw new IllegalArgumentException("Loan amounts may not be negative or missing");
        }
        if (stateBankRate.compareTo(Rate.ZERO) < 0) {
            throw new IllegalArgumentException("The Bank's rate may not be negative, but was " + stateBankRate);
        }
    }
}
