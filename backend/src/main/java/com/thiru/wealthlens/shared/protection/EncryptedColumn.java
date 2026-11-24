package com.thiru.wealthlens.shared.protection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link EncryptedField}'s JPA counterpart (W-337a, D-937): stores a text column encrypted, named on the field with
 * {@code @Convert(converter = EncryptedColumn.class)}. Scoped to text only — no insurance or auth column that moves on this
 * branch encrypts a date or an enum, so those forms are not built speculatively; {@link EncryptedField} still carries them
 * for MongoDB's remaining collections.
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedColumn implements AttributeConverter<String, String> {

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null ? null : cipher.decrypt(stored);
    }
}
