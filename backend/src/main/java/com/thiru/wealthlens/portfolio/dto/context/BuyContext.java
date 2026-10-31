package com.thiru.wealthlens.portfolio.dto.context;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One lot a V2 sale consumed.
 *
 * @param assetType what the lot was recorded as at its buy — the fact the P&L's split falls back on when the sale
 *     itself does not say (W-160d1, D-367)
 */
public record BuyContext(double quantity, LocalDate date, double price, Optional<AssetType> assetType) {
}
