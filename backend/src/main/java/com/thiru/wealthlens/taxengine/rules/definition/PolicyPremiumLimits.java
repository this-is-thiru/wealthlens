package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The premium a capped life policy may not exceed in any year for its sums to be excluded (S2 Schedule II Sl. 2 col. C
 * (a), (b); 1961 s.10(10D) fourth and sixth provisos, with the eighth as FA 2025 substituted it; D-205): one limit for
 * a unit linked policy issued on or after one date, another for any other policy issued on or after a later date, and
 * none for a policy an International Financial Services Centre insurance office issued.
 *
 * @param unitLinkedFrom the issue date from which a unit linked policy is capped
 * @param unitLinkedLimit its limit
 * @param otherFrom the issue date from which any other policy is capped
 * @param otherLimit its limit
 * @param ifscIssuedFrom the issue date from which an IFSC office's policy is free of the limits — empty where the year's
 *     text gives none and every such policy is free (1961 s.10(10D) eighth proviso, FA 2025 s.6(e))
 */
public record PolicyPremiumLimits(LocalDate unitLinkedFrom, Money unitLinkedLimit, LocalDate otherFrom,
        Money otherLimit, Optional<LocalDate> ifscIssuedFrom) implements RuleDefinition {

    public PolicyPremiumLimits {
        if (unitLinkedFrom == null || otherFrom == null || ifscIssuedFrom == null) {
            throw new IllegalArgumentException("Policy premium limits need both issue dates, and Optional.empty()"
                    + " where an IFSC office's policies are free whenever issued");
        }
        for (Money limit : new Money[] {unitLinkedLimit, otherLimit}) {
            if (limit == null || !limit.isGreaterThan(Money.ZERO)) {
                throw new IllegalArgumentException("A policy premium limit must be above nil, but was " + limit);
            }
        }
    }

    /** The limit a policy's annual premium may not exceed, if its kind and issue date make it a capped policy. */
    public Optional<Money> limitFor(LocalDate issuedOn, boolean unitLinked, boolean issuedByIfscOffice) {
        if (issuedByIfscOffice && ifscIssuedFrom.map(from -> !issuedOn.isBefore(from)).orElse(true)) {
            return Optional.empty();
        }
        if (unitLinked) {
            return issuedOn.isBefore(unitLinkedFrom) ? Optional.empty() : Optional.of(unitLinkedLimit);
        }
        return issuedOn.isBefore(otherFrom) ? Optional.empty() : Optional.of(otherLimit);
    }
}
