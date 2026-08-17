package com.thiru.wealthlens.helper.dto.helper;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import java.util.List;

public record ProfitLossDto(
        String transactionId,
        AssetRequest assetRequest,
        List<AssetEntity> stockEntities
) {
}
