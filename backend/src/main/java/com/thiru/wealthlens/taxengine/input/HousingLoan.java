package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * Capital the assessee borrowed for a house, with the interest on it for the year (S2 s.22(1)(b), (c), (2)–(6); 1961
 * s.24(b), s.25; {@code DR 05} M3.2; D-156). The figures are the assessee's own, not the whole property's: each
 * co-owner has the own-residence limit individually (S2 s.24(2); 1961 s.26(2)), so a co-owner enters the interest they
 * pay and no share is applied to it.
 *
 * @param use what the capital was borrowed for
 * @param borrowedOn when the capital was borrowed; it starts the five years for the higher limit
 * @param interestPayable the interest payable on it for the tax year
 * @param preConstructionInterest the interest payable for the period before the tax year the house was acquired or
 *     constructed, as already reduced by any part of it allowed under another provision (S2 s.22(3); 1961 s.24(b)
 *     Explanation) — the whole of it, of which each of five years takes a fifth
 * @param interestDeductedUnderChapterEight the part of {@code interestPayable} the assessee deducts under S2 s.130 or
 *     s.131 (1961 s.80EE, s.80EEA) instead, which no other provision may then allow (s.130(4), s.131(3))
 * @param lenderCertificateHeld whether the assessee furnishes the lender's certificate of S2 s.22(2)(a)(ii), (4)
 * @param payableOutsideIndiaUntaxed whether the interest is payable outside India with no tax paid or deducted on it
 *     and no agent in India (S2 s.22(6); 1961 s.25)
 */
public record HousingLoan(
        LoanUse use,
        LocalDate borrowedOn,
        Money interestPayable,
        Money preConstructionInterest,
        Money interestDeductedUnderChapterEight,
        boolean lenderCertificateHeld,
        boolean payableOutsideIndiaUntaxed) {

    public HousingLoan {
        if (use == null || borrowedOn == null) {
            throw new IllegalArgumentException("A housing loan needs its use and the date the capital was borrowed");
        }
        for (Money figure : new Money[] {interestPayable, preConstructionInterest, interestDeductedUnderChapterEight}) {
            if (figure == null || figure.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A housing loan needs every figure, none negative; enter 0 where"
                        + " nil");
            }
        }
        if (interestDeductedUnderChapterEight.isGreaterThan(interestPayable)) {
            throw new IllegalArgumentException("Interest deducted under s.130 or s.131 of " + interestDeductedUnderChapterEight
                    + " is more than the " + interestPayable + " payable on the loan");
        }
        if (use != LoanUse.ACQUISITION_OR_CONSTRUCTION && preConstructionInterest.isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("Pre-construction interest arises only on capital borrowed to acquire or"
                    + " construct the house (S2 s.22(1)(c); 1961 s.24(b) Explanation)");
        }
    }
}
