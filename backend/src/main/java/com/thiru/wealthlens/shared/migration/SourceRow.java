package com.thiru.wealthlens.shared.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One source document as the row it becomes (W-337g): the columns it fills and their values, and the rows its lists become in
 * each child table (W-337g2a1). A column it leaves out keeps the table's default, as a field the document never stored did.
 *
 * @param id the primary key, as text
 * @param children each child table's rows from this document, the parent column left out
 */
record SourceRow(String id, Map<String, Object> values, Map<String, List<Map<String, Object>>> children) {

    SourceRow {
        values = Map.copyOf(values);
        children = children.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().map(Map::copyOf).toList()));
    }

    /** A row from a document holding no list. */
    SourceRow(String id, Map<String, Object> values) {
        this(id, values, Map.of());
    }
}
