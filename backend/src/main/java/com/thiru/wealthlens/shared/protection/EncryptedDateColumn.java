package com.thiru.wealthlens.shared.protection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link EncryptedField}'s JPA counterpart for a {@code LocalDate} (W-337f): stores the date's {@code toString()} form
 * encrypted, named on the field with {@code @Convert(converter = EncryptedDateColumn.class)}. {@link EncryptedColumn}
 * stays scoped to text; a date needs its own converter because JPA, unlike Mongo's {@code MongoValueConverter}, binds
 * one converter class to one concrete attribute type, not every type a field might declare.
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
