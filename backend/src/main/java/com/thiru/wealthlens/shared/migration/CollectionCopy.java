package com.thiru.wealthlens.shared.migration;

import java.util.List;
import java.util.Map;

/**
 * One MongoDB collection and the PostgreSQL table the copier fills from it (W-337g, D-953 C1a). A stored key reaches the
 * column of its own name, or of its snake_case when the build that wrote it used the Java name; {@code columnsByKey} names
 * the keys {@code m1.6.1} stored under another name than the column holds.
 *
 * @param collection the source collection, as {@code m1.6.1} names it
 * @param table the target table
 * @param columnsByKey a stored key to the column it fills, where the two names differ
 * @param children the lists the documents hold, each filling a child table (W-337g2a1)
 */
public record CollectionCopy(String collection, String table, Map<String, String> columnsByKey, List<ChildCopy> children) {

    public CollectionCopy {
        columnsByKey = Map.copyOf(columnsByKey);
        children = List.copyOf(children);
    }

    /** A collection copied to the table of its own name, every key to the column of its name. */
    static CollectionCopy sameName(String collection) {
        return new CollectionCopy(collection, collection, Map.of(), List.of());
    }
}
