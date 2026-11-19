package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * Specified securities or sweat equity shares a current or former employer allotted or transferred free or at a
 * concession (S2 s.17(1)(d), (4); 1961 s.17(2)(vi); D-121): the fair market value on the exercise date, less what
 * the employee paid.
 *
 * @param employment which entered employment — current or former — allotted them, by position
 * @param allottedOn the date allotted or transferred, which places the income in its tax year
 * @param exercisedOn the date the option was exercised, which the fair market value is taken on
 * @param units how many shares or securities
 * @param pricePaidPerUnit what the employee paid, or had recovered, for each
 * @param basis how the fair market value is found, with its prices or valuation
 */
public record EmployerShareAllotment(
        int employment,
        LocalDate allottedOn,
        LocalDate exercisedOn,
        int units,
        Money pricePaidPerUnit,
        FairMarketValueBasis basis) {

    public EmployerShareAllotment {
        if (allottedOn == null || exercisedOn == null || pricePaidPerUnit == null || basis == null) {
            throw new IllegalArgumentException("A share allotment needs its dates, price and fair-market-value basis");
        }
        if (employment < 0 || units < 0 || pricePaidPerUnit.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A share allotment's position, units and price may not be negative");
        }
        if (exercisedOn.isAfter(allottedOn)) {
            throw new IllegalArgumentException("Shares are allotted on or after the option is exercised; exercised "
                    + exercisedOn + " is after allotted " + allottedOn);
        }
        requireBasisDatedFor(basis, exercisedOn);
    }

    /** The basis's own date must fall where the Rule puts it relative to the exercise of the option. */
    private static void requireBasisDatedFor(FairMarketValueBasis basis, LocalDate exercisedOn) {
        if (basis instanceof FairMarketValueBasis.ListedNotTraded untraded
                && !untraded.onDate().isBefore(exercisedOn)) {
            throw new IllegalArgumentException("An untraded share's closing price is from a day before the exercise"
                    + " date (" + exercisedOn + "), not " + untraded.onDate());
        }
        if (basis instanceof FairMarketValueBasis.MerchantBankerValue banker
                && banker.specifiedDate().isAfter(exercisedOn)) {
            throw new IllegalArgumentException("A merchant banker's specified date is on or before the exercise date ("
                    + exercisedOn + "), not " + banker.specifiedDate());
        }
    }
}
