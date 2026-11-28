package com.thiru.wealthlens.portfolio.entity;

import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Serializes {@code List<CorporateActionEntity>} to and from a {@code TEXT} JSON column (W-337e, D-949 C2a).
 *
 * <p>Each entry is this entity's own <b>snapshot copy</b> of a corporate action at the time it applied — not a
 * reference to the {@code corporate_action} table (W-337d1). {@code CorporateActionEntity} is itself a JPA
 * {@code @Entity} with its own table, so it cannot also be the element type of an {@code @ElementCollection} without
 * conflating "this lot's copy" with "a row in {@code corporate_action}"; no query anywhere filters inside the list.
 *
 * <p>A copy keeps the action's audit fields (W-341). Jackson does not apply {@code AuditColumns}' {@code @Convert}; this
 * converter encrypted {@code createdBy} / {@code updatedBy} itself (W-401, D-956). Since W-428a (D-1036) they are written plain
 * and read plain or in the old {@code k1:} form ({@link FieldCipher#reveal}), on the serialized tree.
 */
@Component
@Converter
@RequiredArgsConstructor
public class CorporateActionListConverter implements AttributeConverter<List<CorporateActionEntity>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<CorporateActionEntity>> TYPE = new TypeReference<>() { };
    private static final String AUDIT = "auditMetadata";
    private static final List<String> ACTORS = List.of("createdBy", "updatedBy");

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(List<CorporateActionEntity> attribute) {
        if (attribute == null) {
            return null;
        }
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<CorporateActionEntity> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        JsonNode copies = MAPPER.readTree(dbData);
        rewriteActors(copies, cipher::reveal);
        return MAPPER.convertValue(copies, TYPE);
    }

    private static void rewriteActors(JsonNode copies, UnaryOperator<String> rewrite) {
        for (JsonNode copy : copies) {
            if (copy.get(AUDIT) instanceof ObjectNode audit) {
                for (String actor : ACTORS) {
                    JsonNode value = audit.get(actor);
                    if (value != null && value.isString()) {
                        audit.put(actor, rewrite.apply(value.asString()));
                    }
                }
            }
        }
    }
}
