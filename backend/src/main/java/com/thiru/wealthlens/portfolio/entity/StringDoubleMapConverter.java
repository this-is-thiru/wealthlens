package com.thiru.wealthlens.portfolio.entity;

import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

/**
 * Serializes {@code Map<String, Double>} to and from a {@code TEXT} JSON column (W-337e, D-949 C2a's pattern), for
 * {@code TradeOutcomeEntity.buyChargeBreakup} / {@code .sellChargeBreakup} — read only in memory, never queried into
 * (unlike {@code UserChargeEntity.amountByCode}, which W-337d2 D-947 C2a made a queryable child table because
 * {@code existsChargeFor*} filters inside it).
 */
@Converter
public class StringDoubleMapConverter extends JsonColumnConverter<Map<String, Double>> {

    private static final TypeReference<Map<String, Double>> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<Map<String, Double>> type() {
        return TYPE;
    }

    @Override
    protected Map<String, Double> empty() {
        return new LinkedHashMap<>();
    }
}
