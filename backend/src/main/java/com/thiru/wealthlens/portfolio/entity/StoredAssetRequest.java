package com.thiru.wealthlens.portfolio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * The stored form of {@code AssetRequest}, as a Jackson mix-in (W-408, D-975): a held trade's request keeps the execution time
 * the Excel upload read ({@code AssetRequestParser.setBrokerOrderTime}), which {@code AssetRequest} hides from API callers with
 * {@code @JsonIgnore} and which a redrive reads first ({@code AssetRequest.orderExecutionDateTime()}). MongoDB stored it, as
 * Spring Data ignores {@code @JsonIgnore}; this keeps it in {@code asset_request_json}. Only the storing mapper registers it, so
 * the request contract is unchanged; the rows moved from MongoDB in W-337i hold the same form (W-337g2b2).
 */
public abstract class StoredAssetRequest {

    @JsonIgnore(false)
    @JsonProperty
    public abstract LocalDateTime getOrderExecutionDateTime();

    @JsonIgnore(false)
    @JsonProperty
    public abstract void setOrderExecutionDateTime(LocalDateTime orderExecutionDateTime);
}
