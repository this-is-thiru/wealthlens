package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * What a relief is applied to.
 *
 * @param totalIncome the income the relief's thresholds are tested against
 * @param taxBeforeRelief the tax the relief comes off — before the rebate for a rebate, tax and
 *     surcharge for surcharge relief
 * @param unrelievedAt how to price another income with relief disabled; needed only by
 *     {@link ThresholdLadderCeiling}, which refuses to run without it
 */
public record ReliefContext(Money totalIncome, Money taxBeforeRelief, Optional<UnrelievedTax> unrelievedAt) {

    public ReliefContext {
        if (totalIncome == null || taxBeforeRelief == null) {
            throw new IllegalArgumentException("A ReliefContext needs the total income and the tax before relief");
        }
        if (unrelievedAt == null) {
            throw new IllegalArgumentException(
                    "ReliefContext.unrelievedAt may not be null; pass Optional.empty() where no relief needs it");
        }
    }
}
