package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A movable asset of the employer transferred to the employee or a household member (S4 r.15(5) Table IV Sl. 8; Rules
 * 1962 r.3(7)(viii); D-119): its cost less normal wear and tear for each completed year the employer used it, less the
 * price paid.
 *
 * @param employment which entered employment transferred it, by position
 * @param kind the class that sets its wear and tear
 * @param cost its actual cost to the employer
 * @param completedYears the completed years the employer used it
 * @param pricePaid what the employee paid, or had recovered, for it
 */
public record EmployerAssetTransfer(
        int employment, TransferredAssetKind kind, Money cost, int completedYears, Money pricePaid) {

    public EmployerAssetTransfer {
        if (kind == null || cost == null || pricePaid == null) {
            throw new IllegalArgumentException("A transferred asset needs its kind, cost and price");
        }
        if (employment < 0 || completedYears < 0 || cost.isLessThan(Money.ZERO) || pricePaid.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A transferred asset's position, years and amounts may not be negative");
        }
    }
}
