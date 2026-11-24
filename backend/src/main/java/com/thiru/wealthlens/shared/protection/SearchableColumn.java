package com.thiru.wealthlens.shared.protection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SearchableIdentifier}'s JPA counterpart (W-337a, D-937): stores a text column encrypted yet still queryable by equality
 * (W-253's scheme), named on the field with {@code @Convert(converter = SearchableColumn.class)}. The same value always stores
 * the same form (the nonce is the value's keyed digest, {@link FieldCipher#encryptSearchable}), so a derived query or a JPQL
 * {@code WHERE} naming the Java property matches without being rewritten. Scoped to text only — no insurance or auth column
 * that moves on this branch is a list of searchable identifiers, unlike {@link SearchableIdentifier}'s Mongo list form, so that
 * case is not built speculatively.
 */
@Component
@Converter
@RequiredArgsConstructor
public class SearchableColumn implements AttributeConverter<String, String> {

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cipher.encryptSearchable(attribute);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null ? null : cipher.decrypt(stored);
    }
}
