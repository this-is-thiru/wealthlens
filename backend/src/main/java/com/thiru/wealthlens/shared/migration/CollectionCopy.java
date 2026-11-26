package com.thiru.wealthlens.shared.migration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One MongoDB collection and the PostgreSQL table the copier fills from it (W-337g, D-953 C1a). A stored key reaches the
 * column of its own name, or of its snake_case when the build that wrote it used the Java name; {@code columnsByKey} names
 * the keys {@code m1.6.1} stored under another name than the column holds.
 *
 * @param collection the source collection, as {@code m1.6.1} names it
 * @param table the target table
 * @param columnsByKey a stored key to the column it fills, where the two names differ
 * @param embedded the keys holding a sub-document whose own keys are mapped as the document's are (W-337g2a2)
 * @param children the lists and maps the documents hold, each filling a child table (W-337g2a1)
 * @param jsonCopies the values the application keeps as JSON in one column (W-337g2b1)
 * @param localHeldKeys the top-level keys holding a time the application held in the JVM's local zone, so stored as its true
 *     instant and read in UTC, as audit times are (W-337g2 C4a, W-337g2b2)
 */
public record CollectionCopy(String collection, String table, Map<String, String> columnsByKey, Set<String> embedded,
        List<ChildCopy> children, List<JsonCopy> jsonCopies, Set<String> localHeldKeys) {

    public CollectionCopy {
        columnsByKey = Map.copyOf(columnsByKey);
        embedded = Set.copyOf(embedded);
        children = List.copyOf(children);
        jsonCopies = List.copyOf(jsonCopies);
        localHeldKeys = Set.copyOf(localHeldKeys);
    }

    /** A collection copied to the table of its own name, every key to the column of its name. */
    static CollectionCopy sameName(String collection) {
        return new CollectionCopy(collection, collection, Map.of(), Set.of(), List.of(), List.of(), Set.of());
    }
}
