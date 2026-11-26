package com.thiru.wealthlens.shared.migration;

import java.lang.reflect.Type;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * mapped by the same rules, a sub-document's key reaching the column of its snake_case as a document's does (W-337g2a1,
 * W-337g2a3); a key {@link CollectionCopy#embedded} names holds a sub-document whose keys are
 * mapped as the document's own are (W-337g2a2).
 */
final class RowMapping {

    private static final String ID = "_id";
    private static final String TYPE_HINT = "_class";
    private static final String AUDIT = "audit_metadata";

    private RowMapping() {
    }

    static SourceRow of(Document document, CollectionCopy copy, TargetTable table, List<ChildTable> children,
            Map<String, Type> jsonTypes, ZoneId writtenZone) {
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
                subDocument(value, AUDIT, where).forEach((auditKey, auditValue) -> put(values, table, snakeCase(auditKey), auditValue,
                        ZoneOffset.UTC, where));
            } else if (copy.embedded().contains(key)) {
                subDocument(value, key, where).forEach((embeddedKey, embeddedValue) -> {
                    if (!embeddedKey.equals(TYPE_HINT) && embeddedValue != null) {
                        mapKey(embeddedKey, embeddedValue, new Destination(copy, table, childByKey, jsonTypes, values, childRows), writtenZone, where);
                    }
                });
            } else {
                mapKey(key, value, new Destination(copy, table, childByKey, jsonTypes, values, childRows), writtenZone, where);
            }
        }
        Object id = values.get(table.primaryKey());
        if (id == null) {
            throw new IllegalStateException(where + ": no " + ID);
        }
        return new SourceRow(id.toString(), values, childRows);
    }

    /** Where a document's keys go: the row's columns, or the rows of the child table a key names. */
    private record Destination(CollectionCopy copy, TargetTable table, Map<String, ChildTable> childByKey, Map<String, Type> jsonTypes,
            Map<String, Object> values, Map<String, List<Map<String, Object>>> childRows) {
    }

    private static void mapKey(String key, Object value, Destination destination, ZoneId writtenZone, String where) {
        Optional<JsonCopy> json = destination.copy().jsonCopies().stream().filter(each -> each.key().equals(key)).findFirst();
        if (json.isPresent()) {
            mapJson(json.get(), value, destination, writtenZone, where);
            return;
        }
        ChildTable child = destination.childByKey().get(key);
        if (child == null) {
            put(destination.values(), destination.table(), columnFor(key, destination.copy(), destination.table()), value, writtenZone,
                    where);
        } else if (child.copy().keyColumn().isPresent()) {
            destination.childRows().put(child.name(), entryRows(child, subDocument(value, key, where), writtenZone, where));
        } else {
            destination.childRows().put(child.name(), childRows(child, value, writtenZone, where));
        }
    }

    /** A JSON column built by the walk, and each sub-key that also fills a column of the row (W-337g2b1). */
    private static void mapJson(JsonCopy json, Object value, Destination destination, ZoneId writtenZone, String where) {
        String text = JsonWalk.json(value, destination.jsonTypes().get(json.key()), writtenZone, where + ", " + json.key());
        put(destination.values(), destination.table(), json.column(), text, writtenZone, where);
        json.columnsBySubKey().forEach((subKey, column) -> put(destination.values(), destination.table(), column,
                subDocument(value, json.key(), where).get(subKey), writtenZone, where));
    }

    private static Document subDocument(Object value, String key, String where) {
        if (!(value instanceof Document subDocument)) {
            throw new IllegalStateException(where + ": " + key + " is not a sub-document");
        }
        return subDocument;
    }

    /** A map's entries, each its key and its value; a null value is refused, as a list's null entry is. */
    private static List<Map<String, Object>> entryRows(ChildTable child, Document map, ZoneId writtenZone, String where) {
        List<Map<String, Object>> rows = new ArrayList<>();
        map.forEach((mapKey, mapValue) -> {
            if (mapValue == null) {
                throw new IllegalStateException(where + ": " + child.copy().key() + " holds a null");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            put(row, child.name(), child.typeByColumn(), child.copy().keyColumn().orElseThrow(), mapKey, writtenZone, where);
            put(row, child.name(), child.typeByColumn(), child.copy().valueColumn().orElseThrow(), mapValue, writtenZone, where);
            rows.add(row);
        });
        return rows;
    }

    private static List<Map<String, Object>> childRows(ChildTable child, Object list, ZoneId writtenZone, String where) {
        if (!(list instanceof List<?> entries)) {
            throw new IllegalStateException(where + ": " + child.copy().key() + " is not a list");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int position = 0; position < entries.size(); position++) {
            Object entry = entries.get(position);
            if (entry == null) {
                throw new IllegalStateException(where + ": " + child.copy().key() + " holds a null");
            }
            Map<String, Object> row = childRow(child, entry, writtenZone, where);
            int at = position;
            child.copy().orderColumn().ifPresent(column -> put(row, child.name(), child.typeByColumn(), column, at, writtenZone, where));
            rows.add(row);
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
                put(row, child.name(), child.typeByColumn(), snakeCase(key), value, writtenZone, where);
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
