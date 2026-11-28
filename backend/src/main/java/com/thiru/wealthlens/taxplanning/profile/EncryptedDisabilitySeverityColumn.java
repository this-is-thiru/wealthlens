package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A {@link DisabilitySeverity} that was stored encrypted (W-337f) as the constant's name, named on the field with
 * {@code @Convert(converter = EncryptedDisabilitySeverityColumn.class)}. Since W-428a (D-1036) it writes the name plain and reads
 * plain or the old {@code k1:} form; W-428c removes it. Lives in
 * {@code taxplanning}, not {@code shared.protection} beside {@code EncryptedColumn}/{@code EncryptedDateColumn},
 * because {@code shared}'s allowed dependencies are {@code portfolio} only — it cannot import a {@code taxengine}
 * type the way this converter must.
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedDisabilitySeverityColumn implements AttributeConverter<DisabilitySeverity, String> {

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(DisabilitySeverity attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public DisabilitySeverity convertToEntityAttribute(String stored) {
        return stored == null ? null : DisabilitySeverity.valueOf(cipher.reveal(stored));
    }
}
