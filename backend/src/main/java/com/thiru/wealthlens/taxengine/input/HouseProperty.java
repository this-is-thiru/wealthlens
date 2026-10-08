package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A building or its appurtenant land the assessee owns, or is deemed to own (S2 s.25; 1961 s.27), as entered for the
 * head "Income from house property" (S2 ss.20–24; 1961 ss.22–26; D-155). The figures are the whole property's; the
 * assessee's share is applied to the income computed from them (S2 s.24(1); 1961 s.26). The loans are the assessee's
 * own, so their interest is deducted after the share (D-156).
 *
 * @param occupancy how the owner used it in the year
 * @param expectedLettingValue the sum it might reasonably be expected to let for from year to year
 * @param rentReceivable the actual rent received or receivable for the year, where let
 * @param vacantPartOfYear whether, being let, it was vacant for the whole or part of the year
 * @param localTaxesPaid the local authority's taxes on it the owner actually paid in the year
 * @param ownershipShare the assessee's definite and ascertainable share — 100% where owned alone
 * @param arrearsReceived arrears of rent, or unrealised rent realised, received from a tenant in the year (S2 s.23;
 *     1961 s.25A)
 * @param acquiredOrConstructedOn when the house was acquired or its construction completed, which fixes the five
 *     pre-construction instalments and the five years for the higher limit (S2 s.22(1)(c), (2)(a)(i); 1961 s.24(b));
 *     needed where a loan was borrowed to acquire or construct it
 * @param loans the capital the assessee borrowed for it, with the interest on each (D-156); empty where none
 */
public record HouseProperty(
        HouseOccupancy occupancy,
        Money expectedLettingValue,
        Money rentReceivable,
        boolean vacantPartOfYear,
        Money localTaxesPaid,
        Rate ownershipShare,
        Money arrearsReceived,
        Optional<LocalDate> acquiredOrConstructedOn,
        List<HousingLoan> loans) {

    public HouseProperty {
        if (occupancy == null || ownershipShare == null) {
            throw new IllegalArgumentException("A house property needs its occupancy and the assessee's share");
        }
        for (Money figure : new Money[] {expectedLettingValue, rentReceivable, localTaxesPaid, arrearsReceived}) {
            if (figure == null || figure.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A house property needs every figure, none negative; enter 0"
                        + " where nil");
            }
        }
        if (ownershipShare.compareTo(Rate.ZERO) <= 0 || ownershipShare.compareTo(Rate.percent("100")) > 0) {
            throw new IllegalArgumentException("A share in a house property is more than nil and at most the whole,"
                    + " but was " + ownershipShare.asPercentLabel());
        }
        if (occupancy != HouseOccupancy.LET && (rentReceivable.isGreaterThan(Money.ZERO) || vacantPartOfYear)) {
            throw new IllegalArgumentException("Rent or a vacancy means the house was let at some time in the year;"
                    + " enter it as LET (S2 s.21(7)(b); 1961 s.23(3))");
        }
        loans = requireLoansDated(acquiredOrConstructedOn, loans);
    }

    /** A copy of the loans, refused where one borrowed to acquire or construct lacks the date it was done. */
    private static List<HousingLoan> requireLoansDated(Optional<LocalDate> acquiredOrConstructedOn,
            List<HousingLoan> loans) {
        if (acquiredOrConstructedOn == null || loans == null) {
            throw new IllegalArgumentException("A house property needs Optional.empty() where the acquisition date is"
                    + " not needed and an empty list where nothing was borrowed");
        }
        boolean borrowedToAcquire = loans.stream().anyMatch(loan -> loan.use() == LoanUse.ACQUISITION_OR_CONSTRUCTION);
        if (borrowedToAcquire && acquiredOrConstructedOn.isEmpty()) {
            throw new IllegalArgumentException("Capital borrowed to acquire or construct a house needs the date it was"
                    + " acquired or constructed (S2 s.22(1)(c), (2)(a)(i))");
        }
        return List.copyOf(loans);
    }
}
