package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A sum of money donated in the tax year, for s.133 (S2; D-142). Only money counts (s.133(4)), so only an amount is
 * entered. The donee's own conditions — an approval or notification, a resident's Clean Ganga Fund donation, a sum
 * other than Corporate Social Responsibility spend — are conditions of entry.
 *
 * @param clause which part of s.133(1) the donee falls under
 * @param amount the sum donated
 * @param paidInCash whether it was paid in cash — over the pack's limit, nothing is allowed (s.133(5))
 * @param reportedByDonee whether the donee furnished the donation's information — read only for a charitable
 *     institution, the one clause s.133(6) reaches
 */
public record Donation(DonationClause clause, Money amount, boolean paidInCash, boolean reportedByDonee) {

    public Donation {
        if (clause == null || amount == null) {
            throw new IllegalArgumentException("A donation needs its clause and amount");
        }
        if (!amount.isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("A donation's amount must be more than nil, but was " + amount);
        }
    }
}
