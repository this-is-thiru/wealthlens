package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.portfolio.entity.JsonColumnConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

/**
 * Serializes {@link WorkbookImport} to and from a {@code TEXT} JSON column (W-337f, D-951 C4a).
 *
 * <p>{@code empty()} returns {@code null}, not a default instance — a column with no stored JSON means this
 * revision's change never came from a salary workbook, which {@code RevisionResponse}'s
 * {@code Optional.ofNullable(stored.getImportedFrom())} already tells apart from one that did. Defaulting to an
 * empty instance would make every revision look like a workbook import; this is the same bug class
 * {@code RealisedProfitsConverter} was fixed for in W-337e (D-949/D-950), avoided here by design.
 */
@Converter
public class WorkbookImportConverter extends JsonColumnConverter<WorkbookImport> {

    private static final TypeReference<WorkbookImport> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<WorkbookImport> type() {
        return TYPE;
    }

    @Override
    protected WorkbookImport empty() {
        return null;
    }
}
