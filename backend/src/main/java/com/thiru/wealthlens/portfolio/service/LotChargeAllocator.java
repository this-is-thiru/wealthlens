package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.shared.util.money.TMoney;

/**
 * Deducts a buy-side charge from a lot, once, across however many sells consume it.
 *
 * <p><b>The bug this replaces (B-7, B-8).</b> A sell divided the lot's <em>full</em> charge by the
 * quantity still remaining, and nothing recorded what had already been taken. A 3-unit lot carrying
 * ₹10, sold one unit at a time, deducted ₹3.33, then ₹5.00 — half of the whole ₹10 again — then
 * ₹10.00. ₹18.33 against ₹10.00 paid, inflating the cost base and understating the gain, which is
 * the direction that under-reports tax.
 *
 * <p>Taking a share of what is <em>left</em> fixes it, and makes the final sell take the remainder
 * by arithmetic rather than by a special case: when the sell consumes the whole remaining quantity
 * the share is the whole remainder.
 *
 * <p><b>One implementation on purpose.</b> V1 and V2 both allocate, and a second copy is how two
 * of them eventually disagree — the same reasoning that collapsed three copies of paise rounding
 * into {@code TMoney}.
 *
 * <p><b>Deduct once per sell.</b> This mutates the lot, so calling it twice for one sell deducts
 * twice. V1 needs the figure for both the trade outcome and the profit-and-loss record: compute it
 * once and hand it to both, rather than letting each ask.
 */
public final class LotChargeAllocator {

    private LotChargeAllocator() {
    }

    /**
     * @param sellQuantity      how much of the lot this sell takes
     * @param remainingQuantity how much the lot held before this sell
     */
    public static double deductBroker(AssetEntity lot, double sellQuantity, double remainingQuantity) {
        double share = shareOf(lot.getBrokerCharges(), lot.getAllocatedBuyCharges(), sellQuantity, remainingQuantity);
        lot.setAllocatedBuyCharges(TMoney.add(lot.getAllocatedBuyCharges(), share));
        return share;
    }

    /** As {@link #deductBroker}, for the user-entered miscellaneous charge. */
    public static double deductMisc(AssetEntity lot, double sellQuantity, double remainingQuantity) {
        double share = shareOf(lot.getMiscCharges(), lot.getAllocatedBuyMiscCharges(), sellQuantity, remainingQuantity);
        lot.setAllocatedBuyMiscCharges(TMoney.add(lot.getAllocatedBuyMiscCharges(), share));
        return share;
    }

    private static double shareOf(double paid, double alreadyDeducted, double sellQuantity, double remainingQuantity) {
        if (remainingQuantity <= 0) {
            return 0.0;
        }
        double remaining = TMoney.scale(paid - alreadyDeducted);
        if (remaining <= 0) {
            return 0.0;
        }
        return TMoney.scale(remaining * sellQuantity / remainingQuantity);
    }
}
