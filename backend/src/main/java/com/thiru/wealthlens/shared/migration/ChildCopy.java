package com.thiru.wealthlens.shared.migration;

import java.util.Optional;

/**
 * A list or map a stored document holds and the child table the copier fills from it (W-337g2a1, W-337g2a2, D-962). Each
 * entry becomes one row joined to its parent by {@code parentColumn}: a plain value fills {@code valueColumn}, a sub-document
 * fills the column of each of its keys' names, and a map's entry fills {@code keyColumn} and {@code valueColumn}. Each factory
 * sets the columns its shape needs.
 *
 * @param key the list's or map's key in the stored document
 * @param table the child table
 * @param parentColumn the child table's column holding the parent's primary key
 * @param valueColumn the column a plain value or a map's value fills
 * @param orderColumn the column an entry's 0-based position fills, where the application keeps the list's order
 * @param keyColumn the column a map's key fills; present only for a map
 */
public record ChildCopy(String key, String table, String parentColumn, Optional<String> valueColumn, Optional<String> orderColumn,
        Optional<String> keyColumn) {

    /** A list of plain values, each one row with the value in {@code column}. */
    static ChildCopy values(String key, String table, String parentColumn, String column) {
        return new ChildCopy(key, table, parentColumn, Optional.of(column), Optional.empty(), Optional.empty());
    }

    /** A list of plain values whose order the application keeps, by Hibernate's {@code @OrderColumn}. */
    static ChildCopy orderedValues(String key, String table, String parentColumn, String column, String orderColumn) {
        return new ChildCopy(key, table, parentColumn, Optional.of(column), Optional.of(orderColumn), Optional.empty());
    }

    /** A list of sub-documents, each one row with each key in the column of its name. */
    static ChildCopy documents(String key, String table, String parentColumn) {
        return new ChildCopy(key, table, parentColumn, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** A list of sub-documents whose order the application keeps, by Hibernate's {@code @OrderColumn}. */
    static ChildCopy orderedDocuments(String key, String table, String parentColumn, String orderColumn) {
        return new ChildCopy(key, table, parentColumn, Optional.empty(), Optional.of(orderColumn), Optional.empty());
    }

    /** A map, stored as a sub-document: each key and its value one row. */
    static ChildCopy entries(String key, String table, String parentColumn, String keyColumn, String valueColumn) {
        return new ChildCopy(key, table, parentColumn, Optional.of(valueColumn), Optional.empty(), Optional.of(keyColumn));
    }
}
