package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * An Agniveer's Corpus Fund account in the tax year: the Agniveer's own subscription and the Central Government's
 * contribution (S2 s.16(l), s.125; D-140). The Government's contribution is salary (s.16(l)) and both are deducted
 * (s.125(1), (2)). Enrolment in the Agnipath Scheme and subscribing on or after 1 November 2022 are conditions of
 * entry.
 *
 * @param ownSubscription what the Agniveer paid or deposited into the account in the tax year
 * @param governmentContribution what the Central Government contributed to the account in the tax year
 */
public record AgniveerCorpusFund(Money ownSubscription, Money governmentContribution) {

    public AgniveerCorpusFund {
        if (ownSubscription == null || governmentContribution == null) {
            throw new IllegalArgumentException("An Agniveer Corpus Fund account needs both amounts; 0 where nil");
        }
        if (ownSubscription.isLessThan(Money.ZERO) || governmentContribution.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("An Agniveer Corpus Fund account's amounts may not be negative");
        }
    }
}
