package com.thiru.wealthlens.portfolio.entity;

import jakarta.persistence.AttributeConverter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializes an opaque, never-queried-into attribute to and from a {@code TEXT} JSON column, same pattern as
 * brokercharges' {@code ChargeRuleListConverter} (W-337d2, D-947 C1a). Shared by portfolio's own converters
 * (W-337e, D-949 C2a/C3a/C4a) since every one of them repeats the same three lines.
 */
public abstract class JsonColumnConverter<T> implements AttributeConverter<T, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    protected abstract TypeReference<T> type();

    protected abstract T empty();

    @Override
    public String convertToDatabaseColumn(T attribute) {
        return attribute == null ? null : MAPPER.writeValueAsString(attribute);
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return empty();
        }
        return MAPPER.readValue(dbData, type());
    }
}
