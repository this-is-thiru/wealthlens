package com.thiru.wealthlens.shared.protection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A text column that was stored encrypted yet queryable by equality (W-253's scheme, W-337a), named on the field with
 * {@code @Convert(converter = SearchableColumn.class)}. Since W-428a (D-1036) it writes plain — so a query's value goes in plain
 * too, and finds a row only once the rewrite ({@code PlainTextRewrite}) has made the stored value plain — and reads plain or the
 * old {@code k1:} form ({@link FieldCipher#reveal}). W-428c removes it.
 */
@Component
@Converter
@RequiredArgsConstructor
public class SearchableColumn implements AttributeConverter<String, String> {

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
