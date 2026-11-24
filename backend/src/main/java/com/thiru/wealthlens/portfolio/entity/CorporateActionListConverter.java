package com.thiru.wealthlens.portfolio.entity;

import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.type.TypeReference;

/**
 * Serializes {@code List<CorporateActionEntity>} to and from a {@code TEXT} JSON column (W-337e, D-949 C2a).
 *
 * <p>Each entry is this entity's own <b>snapshot copy</b> of a corporate action at the time it applied — not a
 * reference to the {@code corporate_action} table (W-337d1). {@code CorporateActionEntity} is itself a JPA
 * {@code @Entity} with its own table, so it cannot also be the element type of an {@code @ElementCollection} without
 * conflating "this lot's copy" with "a row in {@code corporate_action}"; no query anywhere filters inside the list.
 */
@Converter
public class CorporateActionListConverter extends JsonColumnConverter<List<CorporateActionEntity>> {

    private static final TypeReference<List<CorporateActionEntity>> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<List<CorporateActionEntity>> type() {
        return TYPE;
    }

    @Override
    protected List<CorporateActionEntity> empty() {
        return new ArrayList<>();
    }
}
