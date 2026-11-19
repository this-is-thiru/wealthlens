package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;

/**
 * A payment for health under s.126, and whom it covers (S2 s.126; D-136). The section's limits turn on the person —
 * family or parent, senior citizen or not, insured or not — so a payment names its people, not just its amount.
 *
 * @param kind what the payment is for
 * @param coversAssessee whether it covers the assessee
 * @param familyMembers the {@link FamilySnapshot} members it covers, by position
 * @param amount the amount paid in the tax year
 * @param paidInCash whether it was paid in cash — allowed only for a preventive check-up (s.126(9))
 * @param relevantTaxYears the tax years a lump-sum premium keeps the insurance in force, 1 where it is a year's
 *     (s.126(8)(b), (10))
 */
public record HealthPayment(
        HealthPaymentKind kind,
        boolean coversAssessee,
        List<Integer> familyMembers,
        Money amount,
        boolean paidInCash,
        int relevantTaxYears) {

    public HealthPayment {
        if (kind == null || familyMembers == null || amount == null) {
            throw new IllegalArgumentException("A health payment needs its kind, the people it covers and its amount");
        }
        familyMembers = List.copyOf(familyMembers);
        if (!coversAssessee && familyMembers.isEmpty()) {
            throw new IllegalArgumentException("A health payment must cover someone");
        }
        if (amount.isLessThan(Money.ZERO) || relevantTaxYears < 1
                || familyMembers.stream().anyMatch(member -> member < 0)) {
            throw new IllegalArgumentException("A health payment's amount, positions and years must be valid: "
                    + amount + ", " + familyMembers + ", " + relevantTaxYears);
        }
    }
}
