package com.thiru.wealthlens.shared.protection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A text column that was stored encrypted (W-337a, D-937), named on the field with
 * {@code @Convert(converter = EncryptedColumn.class)}. Since W-428a (D-1036) it writes plain and reads plain or the old
 * {@code k1:} form ({@link FieldCipher#reveal}); W-428c removes it once every database is plain. Text only: a date has
 * {@link EncryptedDateColumn}.
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedColumn implements AttributeConverter<String, String> {

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null ? null : cipher.reveal(stored);
    }
}
