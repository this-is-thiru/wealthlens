package com.thiru.wealthlens.taxplanning.protection;

import java.time.LocalDate;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoValueConverter;
import org.springframework.stereotype.Component;

/**
 * Stores a field encrypted ({@code DR 14} O1.1): named on the field with {@code @ValueConverter(EncryptedField.class)}. Holds the
 * three kinds personal data takes here — text, a date and an enum — and refuses any other, so a new kind is a decision, not a
 * silent {@code toString}.
 */
@Component
@RequiredArgsConstructor
public class EncryptedField implements MongoValueConverter<Object, String> {

    private final FieldCipher cipher;

    @Override
    public String write(Object value, MongoConversionContext context) {
        return cipher.encrypt(switch (value) {
            case String text -> text;
            case LocalDate date -> date.toString();
            case Enum<?> constant -> constant.name();
            default -> throw new IllegalStateException("No encrypted form for a " + value.getClass().getSimpleName());
        });
    }

    @Override
    public Object read(String stored, MongoConversionContext context) {
        String plain = cipher.decrypt(stored);
        Class<?> type = context.getProperty().getType();
        if (type == String.class) {
            return plain;
        }
        if (type == LocalDate.class) {
            return LocalDate.parse(plain);
        }
        if (type.isEnum()) {
            return Arrays.stream(type.getEnumConstants()).filter(constant -> ((Enum<?>) constant).name().equals(plain)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No " + type.getSimpleName() + " named in a stored field"));
        }
        throw new IllegalStateException("No encrypted form for a " + type.getSimpleName());
    }
}
