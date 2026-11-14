package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * A life insurance policy whose premium the taxpayer paid in the tax year (S2 Schedule XV para 1(a), para 2; D-134).
 * Only so much of the premium as is within the policy's share of its actual capital sum assured counts towards s.123,
 * and that share depends on when the policy was issued and whom it covers — so the policy, not just the premium, is
 * entered.
 *
 * @param premiumPaid the premium paid in the tax year
 * @param actualCapitalSumAssured the minimum amount assured on the insured event, without premiums to be returned or
 *     any bonus (para 2(2))
 * @param issuedOn when the policy was issued
 * @param coversDisabilityOrSpecifiedDisease whether it covers the life of a person with a disability or severe
 *     disability (s.154) or suffering from a disease or ailment specified in the rules under s.128
 */
public record LifeInsurancePolicy(
        Money premiumPaid,
        Money actualCapitalSumAssured,
        LocalDate issuedOn,
        boolean coversDisabilityOrSpecifiedDisease) {

    public LifeInsurancePolicy {
        if (premiumPaid == null || actualCapitalSumAssured == null || issuedOn == null) {
            throw new IllegalArgumentException("A life insurance policy needs its premium, sum assured and issue date");
        }
        if (premiumPaid.isLessThan(Money.ZERO) || actualCapitalSumAssured.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A life insurance policy's premium and sum assured may not be negative");
        }
    }
}
