package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.time.LocalDate;

/**
 * One disposal to classify for tax.
 *
 * @param stockCode       carried so the sub-class lookup has what it will need; unused today (D7)
 * @param assetType       what was sold
 * @param segment         how it was traded — decides speculative and derivative treatment before
 *                        holding period is even consulted
 * @param acquisitionDate when the lot being disposed of was acquired
 * @param transferDate    when it was sold
 */
public record TradeClassificationQuery(String stockCode, AssetType assetType, TradeSegment segment,
                                       LocalDate acquisitionDate, LocalDate transferDate) {
}
