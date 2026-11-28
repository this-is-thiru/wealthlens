package com.thiru.wealthlens.shared.protection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stores a {@code LocalDate} encrypted (W-337f, {@code DR 14} O1.1): the date's {@code toString()} form, named on the field
 * with {@code @Convert(converter = EncryptedDateColumn.class)}. {@link EncryptedColumn} stays scoped to text; a date needs its
 * own converter because JPA binds one converter class to one concrete attribute type.
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedDateColumn implements AttributeConverter<LocalDate, String> {

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : cipher.encrypt(attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String stored) {
        return stored == null ? null : LocalDate.parse(cipher.decrypt(stored));
    }
}
