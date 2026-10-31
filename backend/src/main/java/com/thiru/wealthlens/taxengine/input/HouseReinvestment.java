package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * A long-term capital gain put into a residential house, as the taxpayer enters it for the exemption from the charge
 * (2025 s.86; 1961 s.54F; D-248, W-088i).
 *
 * <p>Everything the exemption needs that the original transfer does not carry arrives here, because nothing else
 * knows it: {@code portfolio} records the disposal, not what was done with the money. Which buckets the exemption
 * reaches, over what periods the new house must be bought or built, and the most of its cost that counts, are the rule
 * data's ({@code CG-EXEMPT-RESIDENTIAL-HOUSE}); this record carries the facts alone, entered once per transfer.
 *
 * <p>The counts of other houses are the entry's. Whether their income is chargeable under the head "Income from house
 * property" — the condition each limb of the bar carries (2025 s.86(5); 1961 s.54F(1) proviso) — is a fact no record
 * in this engine holds, so the entry states it by counting the houses it is true of.
 *
 * @param transferDate the date of the original transfer this reinvestment answers; every bucket of that date a row
 *     reaches is measured with it, together (W-088i5)
 * @param newAssetOn when the new house was bought, or built
 * @param constructed whether the new house was built rather than bought — the two limbs have different periods
 * @param newAssetCost what the new house cost
 * @param otherHousesOwned other residential houses owned on the transfer date, besides the new house, whose income is
 *     chargeable under house property
 * @param otherHousesAcquired other residential houses bought or built within the periods the law sets around the
 *     transfer, besides the new house, whose income is chargeable under house property
 */
public record HouseReinvestment(
        LocalDate transferDate,
        LocalDate newAssetOn,
        boolean constructed,
        Money newAssetCost,
        int otherHousesOwned,
        int otherHousesAcquired) {

    public HouseReinvestment {
        if (transferDate == null || newAssetOn == null || newAssetCost == null) {
            throw new IllegalArgumentException(
                    "A reinvestment needs the original transfer's date, the new house's date and its cost");
        }
        if (newAssetCost.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("The new house's cost may not be negative: " + newAssetCost);
        }
        if (otherHousesOwned < 0 || otherHousesAcquired < 0) {
            throw new IllegalArgumentException("Counts of other houses may not be negative, but were "
                    + otherHousesOwned + " owned and " + otherHousesAcquired + " acquired");
        }
    }
}
