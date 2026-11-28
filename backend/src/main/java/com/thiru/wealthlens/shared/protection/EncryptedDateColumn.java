package com.thiru.wealthlens.shared.protection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A {@code LocalDate} that was stored encrypted (W-337f) as its {@code toString()} form, named on the field with
 * {@code @Convert(converter = EncryptedDateColumn.class)}. Since W-428a (D-1036) it writes that form plain and reads plain or the
 * old {@code k1:} form; W-428c removes it. A date needs its own converter because JPA binds one converter class to one type.
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedDateColumn implements AttributeConverter<LocalDate, String> {

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String stored) {
        return stored == null ? null : LocalDate.parse(cipher.reveal(stored));
    }
}
