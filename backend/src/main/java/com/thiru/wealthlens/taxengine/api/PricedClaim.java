package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A claim the computation allows, and what it is worth (W-118 C1a): the inputs run again with the claim's rules withheld,
 * so both figures are measured by the pipeline ({@code D-MARGINAL}), never assumed. Two claims can overlap — own NPS and
 * the investment ceiling — so worths are each "with every other claim as it stands" and need not add up.
 *
 * @param playbook how the claim is made
 * @param allowed what the claim takes off total income
 * @param worth what the claim takes off the bill
 * @param finalAmount the amount finally payable — the bill less credits, with interest and fees, rounded — as the
 *     inputs stand
 * @param finalAmountWithout the same with the claim withheld: where the two are equal, the claim does not change what is
 *     paid, whatever its worth, because the rounding of the amount payable absorbs it (W-121 C1a)
 */
public record PricedClaim(ClaimPlaybook playbook, Money allowed, Money worth, Money finalAmount, Money finalAmountWithout) {

    /** Whether making the claim changes the amount finally payable. */
    public boolean changesFinalAmount() {
        return !finalAmount.equals(finalAmountWithout);
    }
}
