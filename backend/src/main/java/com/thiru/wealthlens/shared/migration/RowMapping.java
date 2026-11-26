package com.thiru.wealthlens.shared.migration;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.Document;

/**
 * Maps a stored MongoDB document to the row its table takes (W-337g, D-953 C1a): {@code _id} to the primary key, the
 * {@code audit_metadata} sub-document to the four audit columns, every other key to its column. Every time is written in UTC
 * (D-954). An audit time was the writing JVM's local time ({@code CurrentDateTimeProvider} calls {@code LocalDateTime.now()},
 * no provider being configured), stored through that same zone, so the stored instant is the true one and is read in UTC;
 * every other time in these collections the application already held in UTC, so it is read in the written zone, which gives
 * back the UTC value it held. A key no column takes is
 * refused rather than dropped, so nothing stored is lost without a word; a key holding {@code null} is left out, as
 * Spring Data read it the same as a missing one. A key a {@link ChildCopy} names becomes that child table's rows, each entry
 * mapped by the same rules (W-337g2a1).
 */
final class RowMapping {

    private static final String ID = "_id";
    private static final String TYPE_HINT = "_class";
    private static final String AUDIT = "audit_metadata";

    private RowMapping() {
    }

    static SourceRow of(Document document, CollectionCopy copy, TargetTable table, List<ChildTable> children, ZoneId writtenZone) {
        String where = copy.collection() + " " + document.get(ID);
        Map<String, ChildTable> childByKey = children.stream().collect(Collectors.toMap(child -> child.copy().key(), child -> child));
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> childRows = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key.equals(TYPE_HINT) || value == null) {
                continue;
            }
            if (key.equals(AUDIT)) {
                if (!(value instanceof Document audit)) {
                    throw new IllegalStateException(where + ": audit_metadata is not a sub-document");
                }
                audit.forEach((auditKey, auditValue) -> put(values, table, snakeCase(auditKey), auditValue, ZoneOffset.UTC, where));
                continue;
            }
            if (childByKey.containsKey(key)) {
                childRows.put(childByKey.get(key).name(), childRows(childByKey.get(key), value, writtenZone, where));
                continue;
            }
            put(values, table, columnFor(key, copy, table), value, writtenZone, where);
        }
        Object id = values.get(table.primaryKey());
        if (id == null) {
            throw new IllegalStateException(where + ": no " + ID);
        }
        return new SourceRow(id.toString(), values, childRows);
    }

    private static List<Map<String, Object>> childRows(ChildTable child, Object list, ZoneId writtenZone, String where) {
        if (!(list instanceof List<?> entries)) {
            throw new IllegalStateException(where + ": " + child.copy().key() + " is not a list");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object entry : entries) {
            if (entry == null) {
                throw new IllegalStateException(where + ": " + child.copy().key() + " holds a null");
            }
            rows.add(childRow(child, entry, writtenZone, where));
        }
        return rows;
    }

    private static Map<String, Object> childRow(ChildTable child, Object entry, ZoneId writtenZone, String where) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (child.copy().valueColumn().isPresent()) {
            put(row, child.name(), child.typeByColumn(), child.copy().valueColumn().get(), entry, writtenZone, where);
            return row;
        }
        if (!(entry instanceof Document subDocument)) {
            throw new IllegalStateException(where + ": " + child.copy().key() + " holds a " + entry.getClass().getSimpleName()
                    + ", not a sub-document");
        }
        subDocument.forEach((key, value) -> {
            if (!key.equals(TYPE_HINT)) {
                put(row, child.name(), child.typeByColumn(), key, value, writtenZone, where);
            }
        });
        return row;
    }

    private static String columnFor(String key, CollectionCopy copy, TargetTable table) {
        if (copy.columnsByKey().containsKey(key)) {
            return copy.columnsByKey().get(key);
        }
        return key.equals(ID) ? table.primaryKey() : snakeCase(key);
    }

    private static void put(Map<String, Object> values, TargetTable table, String column, Object value, ZoneId writtenZone,
            String where) {
        put(values, table.name(), table.typeByColumn(), column, value, writtenZone, where);
    }

    private static void put(Map<String, Object> values, String tableName, Map<String, String> typeByColumn, String column,
            Object value, ZoneId writtenZone, String where) {
        if (value == null) {
            return;
        }
        String type = typeByColumn.get(column);
        if (type == null) {
            throw new IllegalStateException(where + ": " + tableName + " has no column " + column + " for a stored key");
        }
        if (values.containsKey(column)) {
            throw new IllegalStateException(where + ": two stored keys fill " + column);
        }
        try {
            values.put(column, ColumnValue.of(value, type, writtenZone));
        } catch (IllegalStateException refused) {
            throw new IllegalStateException(where + ", " + column + ": " + refused.getMessage(), refused);
        }
    }

    /** {@code stockCode} → {@code stock_code}; a name already in snake_case is unchanged. */
    static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
