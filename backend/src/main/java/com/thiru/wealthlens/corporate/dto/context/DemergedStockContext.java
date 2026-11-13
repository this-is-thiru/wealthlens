package com.thiru.wealthlens.corporate.dto.context;

/**
 * A stock a demerger creates: its share of the parent's cost, and the shares it allots per share held, as the market
 * writes the ratio, {@code new:held} (ITC's "1:10" is one ITC Hotels share for every ten ITC; W-313).
 */
public record DemergedStockContext(
        String stockCode,
        String stockName,
        double pricePercentage,
        int newShares,
        int heldShares
) {
}
