package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.portfolio.entity.JsonColumnConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

/**
 * Serializes {@link DraftStaleness} to and from a {@code TEXT} JSON column (W-337f, D-951 C4a).
 *
 * <p>{@code empty()} returns {@code null}, not a default instance — a column with no stored JSON means this draft's
 * last computation is not known to be stale, which {@code DraftService} and {@code StalenessSweep} both read as
 * meaningful: {@code DraftService} filters on {@code getStaleness() != null} and explicitly clears it with
 * {@code setStaleness(null)}; {@code StalenessSweep} short-circuits on it. Defaulting to an empty instance would make
 * every draft look stale — the same bug class {@code RealisedProfitsConverter} was fixed for in W-337e
 * (D-949/D-950), avoided here by design.
 */
@Converter
public class DraftStalenessConverter extends JsonColumnConverter<DraftStaleness> {

    private static final TypeReference<DraftStaleness> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<DraftStaleness> type() {
        return TYPE;
    }

    @Override
    protected DraftStaleness empty() {
        return null;
    }
}
