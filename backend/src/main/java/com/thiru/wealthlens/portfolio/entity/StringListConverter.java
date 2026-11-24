package com.thiru.wealthlens.portfolio.entity;

import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.type.TypeReference;

/**
 * Serializes {@code List<String>} to and from a {@code TEXT} JSON column (W-337e, D-949 C2a's pattern), for
 * {@code AssetEntity.buyTransactionIds} / {@code .sellTransactionIds} — lot linkage read only in memory, never
 * queried into.
 */
@Converter
public class StringListConverter extends JsonColumnConverter<List<String>> {

    private static final TypeReference<List<String>> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<List<String>> type() {
        return TYPE;
    }

    @Override
    protected List<String> empty() {
        return new ArrayList<>();
    }
}
