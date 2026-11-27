package com.thiru.wealthlens.shared.migration;

import java.util.Map;

/**
 * A stored sub-document, list or map that the application keeps as JSON in one column (W-337g2b1, W-337g2 C2a). The copier
 * builds that JSON by the column's Java type, read from the target entity ({@link JsonWalk}).
 *
 * @param key the key in the stored document
 * @param column the JSON column
 * @param columnsBySubKey a key inside the stored sub-document whose value also fills a column of the row, as the entity's setter
 *     keeps it in step (plans/W-337g2b1.md C3a)
 */
public record JsonCopy(String key, String column, Map<String, String> columnsBySubKey) {

    public JsonCopy {
        columnsBySubKey = Map.copyOf(columnsBySubKey);
    }

    /** A JSON column filled from the key of its own name. */
    static JsonCopy sameName(String key) {
        return new JsonCopy(key, key, Map.of());
    }
}
