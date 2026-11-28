package com.thiru.wealthlens.shared.protection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stores a text column encrypted (W-337a, D-937; {@code DR 14} O1.1), named on the field with
 * {@code @Convert(converter = EncryptedColumn.class)}. Text only: a date has {@link EncryptedDateColumn}, an enum its own
 * converter where one is stored encrypted.
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
