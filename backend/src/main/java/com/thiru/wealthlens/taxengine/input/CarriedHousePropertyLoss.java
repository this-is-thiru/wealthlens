package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A house-property loss carried forward from the tax year it arose, as earlier years left it (S2 s.110; 1961 s.71B;
 * D-218). Consumed, never remaining: what is left is the engine's subtraction (M3.4).
 *
 * @param loss the loss carried forward from that year — what its own set-off left
 * @param attributableToNewRegimeDisallowance the part of it that arose from a deduction the new regime disallows
 *     (S2 s.202(2)(a), (2)(b)(i); 1961 s.115BAC(2)(i), (2)(ii)(a)), which a new-regime year may not use
 * @param absorbedSince how much of it later years have already set off or deemed given full effect to
 */
public record CarriedHousePropertyLoss(Money loss, Money attributableToNewRegimeDisallowance, Money absorbedSince) {

    public CarriedHousePropertyLoss {
        if (loss == null || attributableToNewRegimeDisallowance == null || absorbedSince == null) {
            throw new IllegalArgumentException("A carried house-property loss needs every figure; enter 0 where nil");
        }
        if (loss.isLessThan(Money.ZERO) || attributableToNewRegimeDisallowance.isLessThan(Money.ZERO)
                || absorbedSince.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A carried house-property loss's figures may not be negative");
        }
        if (attributableToNewRegimeDisallowance.isGreaterThan(loss) || absorbedSince.isGreaterThan(loss)) {
            throw new IllegalArgumentException("Of a carried loss of " + loss + ", the attributable part ("
                    + attributableToNewRegimeDisallowance + ") and what is absorbed (" + absorbedSince
                    + ") are each at most the loss");
        }
    }
}
