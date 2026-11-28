package com.thiru.wealthlens.portfolio.entity;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializes {@link AssetRequest} to and from a {@code TEXT} JSON column (W-337e, D-949 C4a) — kept only so a
 * redrive can replay the original request ({@code TemporaryTransactionService}); no query filters inside it.
 *
 * <p>Its mapper writes the stored form, {@link StoredAssetRequest}: the execution time an upload read, hidden from API
 * callers, is kept (W-408).
 */
@Component
@Converter
public class AssetRequestConverter implements AttributeConverter<AssetRequest, String> {

    /** Registers the stored form, so an uploaded trade's execution time survives (W-408). */
    private static final JsonMapper MAPPER = JsonMapper.builder().addMixIn(AssetRequest.class, StoredAssetRequest.class).build();

    @Override
    public String convertToDatabaseColumn(AssetRequest attribute) {
        if (attribute == null) {
            return null;
        }
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public AssetRequest convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return MAPPER.readValue(dbData, AssetRequest.class);
    }
}
