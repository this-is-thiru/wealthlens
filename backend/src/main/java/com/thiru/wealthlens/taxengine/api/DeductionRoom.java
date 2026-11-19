package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * Room left under a deduction's ceiling, and the tax filling it would save (W-114a C2a, D-666): the draft's bill less the
 * bill with the room paid in a contributor its row leaves without a sub-cap ({@code D-MARGINAL}). Nil saved where the
 * regime allows the deduction nothing.
 *
 * @param rule the row whose ceiling it is
 * @param counted what the run allowed under the ceiling
 * @param room what remains of the ceiling, above nil
 * @param taxSaved what paying it in the year would take off the bill
 */
public record DeductionRoom(String rule, Money counted, Money room, Money taxSaved) {
}
