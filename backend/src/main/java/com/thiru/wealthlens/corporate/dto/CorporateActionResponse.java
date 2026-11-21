package com.thiru.wealthlens.corporate.dto;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.entity.model.DemergerDetail;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.time.LocalDate;

/** A corporate action as a caller reads it (W-358c2, D-857): its fields, not who recorded it — every signed-in user reads these. */
public record CorporateActionResponse(String id, String stockCode, String stockName, String toStockCode, String toStockName, CorporateActionType type, AssetType assetType, String description, LocalDate recordDate, LocalDate exDate, LocalDate allotmentDate, int priority, String actionPrice, String ratio, DemergerDetail demergerDetail) {

    public static CorporateActionResponse from(CorporateActionEntity entity) {
        return new CorporateActionResponse(entity.getId(), entity.getStockCode(), entity.getStockName(), entity.getToStockCode(), entity.getToStockName(), entity.getType(), entity.getAssetType(), entity.getDescription(), entity.getRecordDate(), entity.getExDate(), entity.getAllotmentDate(), entity.getPriority(), entity.getActionPrice(), entity.getRatio(), entity.getDemergerDetail());
    }
}
