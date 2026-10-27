package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.shared.util.money.TMoney;

/**
 * What a set of disposals fetched, what it cost and what was charged on each side — the amounts a capital gain is
 * computed from (2025 s.72; 1961 s.48), never the gain itself (W-162a, D-375).
 *
 * <p>Which charges reduce the gain is the law of the year, so it is the tax side's rule data; {@code portfolio} publishes
 * every charge as recorded. Each amount is to the paisa, as a contract note shows it.
 *
 * @param considerationRupees the sale price times the quantity sold
 * @param costRupees the lots' price, as adjusted for corporate actions, times the quantity sold
 * @param acquisition what the buys were charged
 * @param transfer what the sales were charged
 */
public record CapitalGainsAmounts(
        double considerationRupees, double costRupees, SideCharges acquisition, SideCharges transfer) {

    public CapitalGainsAmounts {
        if (acquisition == null || transfer == null) {
            throw new IllegalArgumentException("Capital-gains amounts need both sides' charges; pass SideCharges.NONE"
                    + " for a side that bore none");
        }
        considerationRupees = TMoney.scale(considerationRupees);
        costRupees = TMoney.scale(costRupees);
    }

    /** These amounts and {@code other}'s, as one bucket's. */
    public CapitalGainsAmounts plus(CapitalGainsAmounts other) {
        return new CapitalGainsAmounts(TMoney.add(considerationRupees, other.considerationRupees),
                TMoney.add(costRupees, other.costRupees), acquisition.plus(other.acquisition),
                transfer.plus(other.transfer));
    }
}
