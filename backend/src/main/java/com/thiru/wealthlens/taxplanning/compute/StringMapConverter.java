package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.portfolio.entity.JsonColumnConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

/**
 * Serializes {@code Map<String, String>} to and from a {@code TEXT} JSON column (same pattern as
 * {@code StringDoubleMapConverter}, W-337e, D-949 C2a), for {@code DraftComputation.ruleChecksums} — read only in
 * memory to name rules that changed under a stale computation (W-337f, D-951 C2a), never queried into.
 */
@Converter
public class StringMapConverter extends JsonColumnConverter<Map<String, String>> {

    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<Map<String, String>> type() {
        return TYPE;
    }

    /** Null, not empty: a computation stored before W-138 recorded no checksums, which readers tell from none changed. */
    @Override
    protected Map<String, String> empty() {
        return null;
    }
}
