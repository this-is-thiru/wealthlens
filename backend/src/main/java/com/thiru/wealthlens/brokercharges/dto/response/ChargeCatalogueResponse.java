package com.thiru.wealthlens.brokercharges.dto.response;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;

/** A charge code of the catalogue as a caller reads it (W-358c2, D-857). */
public record ChargeCatalogueResponse(String code, String displayName, ChargeCategory category, String description, String statutoryReference, EntityStatus status) {

    public static ChargeCatalogueResponse from(ChargeCatalogueEntity entity) {
        return new ChargeCatalogueResponse(entity.getCode(), entity.getDisplayName(), entity.getCategory(), entity.getDescription(), entity.getStatutoryReference(), entity.getStatus());
    }
}
