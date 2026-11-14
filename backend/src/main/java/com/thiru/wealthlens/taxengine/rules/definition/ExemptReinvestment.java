package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * What a row of {@code RS-CG-EXEMPTION} says of a gain put into a residential house: the periods within which the new
 * house must be bought or built, and the most of its cost that counts (2025 s.86(1), (8); 1961 s.54F(1) and its second
 * proviso; W-088i).
 *
 * <p>The periods and the cap are the law's, so they are data: a change in any of them is a new version of the row, not
 * a code change. How the months are counted is the step's, as {@link ShortTermHoldingPeriod}'s are.
 *
 * @param monthsBeforePurchase how long before the transfer a purchase of the new house still serves
 * @param monthsAfterPurchase how long after it one does
 * @param monthsAfterConstruction how long after it construction of the new house must be done
 * @param newAssetCostCap the most of the new house's cost that may be taken into account
 */
public record ExemptReinvestment(
        int monthsBeforePurchase,
        int monthsAfterPurchase,
        int monthsAfterConstruction,
        Money newAssetCostCap) implements RuleDefinition {

    public ExemptReinvestment {
        if (monthsBeforePurchase <= 0 || monthsAfterPurchase <= 0 || monthsAfterConstruction <= 0) {
            throw new IllegalArgumentException("A reinvestment row's periods must each be positive months, but were "
                    + monthsBeforePurchase + " before a purchase, " + monthsAfterPurchase + " after one and "
                    + monthsAfterConstruction + " after construction");
        }
        if (newAssetCostCap == null || newAssetCostCap.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException(
                    "A reinvestment row's cost cap may not be negative: " + newAssetCostCap);
        }
    }
}
