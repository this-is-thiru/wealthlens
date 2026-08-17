package com.thiru.wealthlens.corporate.dto.context;

import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import java.util.List;

public record DemergerContext(
        String stockCode,
        String stockName,
        double pricePercentage,
        double quantityRatio,
        AssetEntity entity,
        List<DemergedStockContext> demergedStocks
) {
}
