package com.thiru.wealthlens.shared.migration;

import java.util.Optional;

/**
 * A list a stored document holds and the child table the copier fills from it (W-337g2a1, D-962, plans/W-337g2a1.md C1a).
 * Each entry becomes one row joined to its parent by {@code parentColumn}: a plain value fills {@code valueColumn}, a
 * sub-document fills the column of each of its keys' names.
 *
 * @param key the list's key in the stored document
 * @param table the child table
 * @param parentColumn the child table's column holding the parent's primary key
 * @param valueColumn the column a plain value fills; empty when the entries are sub-documents
 */
public record ChildCopy(String key, String table, String parentColumn, Optional<String> valueColumn) {

    /** A list of plain values, each one row with the value in {@code column}. */
    static ChildCopy values(String key, String table, String parentColumn, String column) {
        return new ChildCopy(key, table, parentColumn, Optional.of(column));
    }

    /** A list of sub-documents, each one row with each key in the column of its name. */
    static ChildCopy documents(String key, String table, String parentColumn) {
        return new ChildCopy(key, table, parentColumn, Optional.empty());
    }
}
