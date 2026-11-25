package com.thiru.wealthlens.shared.migration;

import java.util.Map;

/**
 * One source document as the row it becomes (W-337g): the columns it fills and their values. A column it leaves out keeps
 * the table's default, as a field the document never stored did.
 *
 * @param id the primary key, as text
 */
record SourceRow(String id, Map<String, Object> values) {

    SourceRow {
        values = Map.copyOf(values);
    }
}
