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
 * {@code TransactionEntity.assetRequest}. Jackson does not understand that annotation, so this converter encrypts
 * both fields itself before serializing (and decrypts them after reading back) — otherwise a second, plaintext
 * copy of the user's email and account holder would appear in the JSON blob, a protection regression this port
 * must not introduce.
 */
@Component
@Converter
@RequiredArgsConstructor
public class AssetRequestConverter implements AttributeConverter<AssetRequest, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(AssetRequest attribute) {
        if (attribute == null) {
            return null;
        }
        AssetRequest copy = MAPPER.readValue(MAPPER.writeValueAsString(attribute), AssetRequest.class);
        copy.setEmail(encrypt(copy.getEmail()));
        copy.setAccountHolder(encrypt(copy.getAccountHolder()));
        return MAPPER.writeValueAsString(copy);
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

    private String encrypt(String value) {
        return value == null ? null : cipher.encryptSearchable(value);
    }

    private String decrypt(String value) {
        return value == null ? null : cipher.decrypt(value);
    }
}
