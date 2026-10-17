package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * A sum received in the tax year under a life insurance policy, bonus included, with the facts that decide whether it
 * is excluded from total income and, where it is not, what it is charged on (S2 Schedule II Sl. 2, s.92(2)(l), S4
 * r.59; 1961 s.10(10D), s.56(2)(xiii), Rules 1962 r.11UACA; D-205).
 *
 * <p>Not entered here: a Keyman policy's sums (an {@link OtherSourceKind#KEYMAN_POLICY} item, or salary) and sums
 * under s.127(4), neither of which Sl. 2 can exclude.
 *
 * @param received the sums received under the policy in the tax year
 * @param issuedOn when the policy was issued
 * @param unitLinked whether it is a unit linked insurance policy (S2 Schedule II Note 1(f))
 * @param onDeath whether the sums were received on the death of a person
 * @param highestAnnualPremium the highest annual premium payable during the policy's term, without the Goods and
 *     Services Tax (Circular No. 15/2023 para 5)
 * @param actualCapitalSumAssured the minimum amount assured on the insured event, without premiums to be returned or
 *     any bonus (S2 Schedule XV para 2(2))
 * @param coversDisabilityOrSpecifiedDisease whether it is a special policy — on the life of a person with a disability
 *     or severe disability (s.154), or suffering from a disease or ailment specified under s.128
 * @param issuedByIfscOffice whether an International Financial Services Centre insurance office issued it
 * @param claimsExemption whether the person claims this policy's consideration exempt — which, for a capped policy (a
 *     unit linked one issued on or after 1 February 2021, another on or after 1 April 2023), puts its premium in the set
 *     the aggregate limit tests (CBDT Circulars No. 2/2022, 15/2023; D-207); a capped policy not claimed is charged
 * @param premiumFrom the first tax year the premium is payable
 * @param premiumTo the last tax year the premium is payable — a surrender ends it
 * @param premiumsNotDeductedOrUsed the premium paid during the term till the receipt that was not claimed as a
 *     deduction and not counted in an earlier year's computation — r.59's B or D
 */
public record LifePolicyReceipt(
        Money received,
        LocalDate issuedOn,
        boolean unitLinked,
        boolean onDeath,
        Money highestAnnualPremium,
        Money actualCapitalSumAssured,
        boolean coversDisabilityOrSpecifiedDisease,
        boolean issuedByIfscOffice,
        boolean claimsExemption,
        TaxYearId premiumFrom,
        TaxYearId premiumTo,
        Money premiumsNotDeductedOrUsed) {

    public LifePolicyReceipt {
        if (issuedOn == null || premiumFrom == null || premiumTo == null) {
            throw new IllegalArgumentException("A life policy receipt needs the policy's issue date and premium years");
        }
        if (premiumTo.startYear() < premiumFrom.startYear()) {
            throw new IllegalArgumentException("A life policy's premium years may not end before they start");
        }
        for (Money figure : new Money[] {received, highestAnnualPremium, actualCapitalSumAssured,
            premiumsNotDeductedOrUsed}) {
            if (figure == null || figure.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A life policy receipt needs every figure, none negative");
            }
        }
        if (!actualCapitalSumAssured.isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("A life policy receipt needs an actual capital sum assured above nil,"
                    + " the ratio's denominator");
        }
    }

    /** Whether the premium is payable in {@code year}. */
    public boolean payableIn(int year) {
        return year >= premiumFrom.startYear() && year <= premiumTo.startYear();
    }
}
