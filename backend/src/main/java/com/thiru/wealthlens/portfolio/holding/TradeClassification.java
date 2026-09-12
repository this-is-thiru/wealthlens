package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;

/**
 * The answer, with the sub-class that produced it and a human-readable reason.
 *
 * @param capitalGainsType how the disposal is taxed
 * @param subClass         the second dimension that decided it, or null when unavailable (D7)
 * @param reason           why — recorded so an {@code UNCLASSIFIED} row says which kind of unknown
 *                         it is rather than leaving a reader to guess
 */
public record TradeClassification(CapitalGainsType capitalGainsType, String subClass, String reason) {
}
