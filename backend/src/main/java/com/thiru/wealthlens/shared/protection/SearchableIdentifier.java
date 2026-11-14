package com.thiru.wealthlens.shared.protection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoValueConverter;
import org.springframework.stereotype.Component;

/**
 * Stores an identifier encrypted yet still queryable (W-253, D-485 C1a): named with {@code @ValueConverter(SearchableIdentifier.class)}
 * on an email, an account holder or a demat account id, or on a list of emails. Spring Data puts a query's value for the field
 * through {@link #write} too, so a derived method, an {@code @Query} and a {@code Criteria} against the entity match the stored
 * form without being rewritten, provided they name the property by its Java name. Text only: an operator with a non-text value
 * ({@code $exists}) on such a field refuses rather than matching nothing.
 */
@Component
@RequiredArgsConstructor
public class SearchableIdentifier implements MongoValueConverter<Object, Object> {

    private final FieldCipher cipher;

    @Override
    public Object write(Object value, MongoConversionContext context) {
        if (value instanceof String text) {
            return cipher.encryptSearchable(text);
        }
        if (value instanceof Collection<?> values) {
            return values.stream().map(each -> write(each, context)).toList();
        }
        throw new IllegalStateException("An identifier is stored and queried as text, not as a " + value.getClass().getSimpleName());
    }

    /** A list reads back mutable, as the entities' own list fields are. */
    @Override
    public Object read(Object stored, MongoConversionContext context) {
        if (stored instanceof String text) {
            return cipher.decrypt(text);
        }
        if (stored instanceof Collection<?> values) {
            return values.stream().map(each -> read(each, context)).collect(Collectors.toCollection(ArrayList::new));
        }
        throw new IllegalStateException("A stored identifier is text, not a " + stored.getClass().getSimpleName());
    }
}
