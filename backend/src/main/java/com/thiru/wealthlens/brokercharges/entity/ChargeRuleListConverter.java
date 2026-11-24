package com.thiru.wealthlens.brokercharges.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializes {@code List<ChargeRule>} to and from a {@code TEXT} JSON column ({@code rules_json}, W-337d2 D-947 C1a).
 * No query filters inside rules; the charge engine always loads whole cards/profiles into memory.
 */
@Converter
public class ChargeRuleListConverter implements AttributeConverter<List<ChargeRule>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<ChargeRule>> RULE_LIST = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<ChargeRule> attribute) {
        if (attribute == null) {
            return null;
        }
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<ChargeRule> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        return MAPPER.readValue(dbData, RULE_LIST);
    }
}
