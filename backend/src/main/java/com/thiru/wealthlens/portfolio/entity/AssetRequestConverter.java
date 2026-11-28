package com.thiru.wealthlens.portfolio.entity;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializes {@link AssetRequest} to and from a {@code TEXT} JSON column (W-337e, D-949 C4a) — kept only so a
 * redrive can replay the original request ({@code TemporaryTransactionService}); no query filters inside it.
 *
 * <p>Under MongoDB, {@code AssetRequest.email} / {@code .accountHolder} carried
 * {@code @ValueConverter(SearchableIdentifier.class)}, so Spring Data encrypted them even nested inside
 * {@code TransactionEntity.assetRequest}; this converter did the same by hand. Since W-428a (D-1036) both are written plain,
 * and read plain or in the old {@code k1:} form ({@link FieldCipher#reveal}); W-428c removes the reading of the old form.
 *
 * <p>Its mapper writes the stored form, {@link StoredAssetRequest}: the execution time an upload read, hidden from API
 * callers, is kept (W-408).
 */
@Component
@Converter
@RequiredArgsConstructor
public class AssetRequestConverter implements AttributeConverter<AssetRequest, String> {

    /** Registers the stored form, so an uploaded trade's execution time survives (W-408). */
    private static final JsonMapper MAPPER = JsonMapper.builder().addMixIn(AssetRequest.class, StoredAssetRequest.class).build();

    private final FieldCipher cipher;

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
        AssetRequest attribute = MAPPER.readValue(dbData, AssetRequest.class);
        attribute.setEmail(decrypt(attribute.getEmail()));
        attribute.setAccountHolder(decrypt(attribute.getAccountHolder()));
        return attribute;
    }

    private String decrypt(String value) {
        return value == null ? null : cipher.reveal(value);
    }
}
