package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * A capped life policy whose consideration in an earlier tax year was claimed exempt — an "old" policy, whose premium
 * counts with this year's claims toward the aggregate limit (1961 s.10(10D) fifth and seventh provisos; CBDT Circulars
 * No. 2/2022 para 4.2 and No. 15/2023 para 4.3, continued by S2 s.536(2)(j); D-207). A policy whose earlier
 * consideration was received but not claimed exempt is not one; nor is a policy merely held.
 *
 * @param issuedOn when the policy was issued
 * @param unitLinked whether it is a unit linked insurance policy — the two classes are tested apart
 * @param annualPremium the premium payable each year, without the Goods and Services Tax (Circular No. 15/2023 para 5)
 * @param premiumFrom the first tax year the premium is payable
 * @param premiumTo the last tax year the premium is payable — a surrender ends it
 */
public record ClaimedPolicy(LocalDate issuedOn, boolean unitLinked, Money annualPremium, TaxYearId premiumFrom,
        TaxYearId premiumTo) {

    public ClaimedPolicy {
        if (issuedOn == null || annualPremium == null || premiumFrom == null || premiumTo == null) {
            throw new IllegalArgumentException("A claimed policy needs its issue date, annual premium and premium"
                    + " years");
        }
        if (annualPremium.isLessThan(Money.ZERO) || premiumTo.startYear() < premiumFrom.startYear()) {
            throw new IllegalArgumentException("A claimed policy's premium may not be negative, nor its premium years"
                    + " end before they start");
        }
    }

    /** Whether the premium is payable in {@code year}. */
    public boolean payableIn(int year) {
        return year >= premiumFrom.startYear() && year <= premiumTo.startYear();
    }
}
