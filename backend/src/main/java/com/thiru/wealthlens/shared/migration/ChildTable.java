package com.thiru.wealthlens.shared.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A child table as the copier sees it (W-337g2a1): its columns and their types, read from the database Flyway built. It has
 * no key of its own, so its rows are written only together with a parent the same run wrote (plans/W-337g2a1.md C2a).
 *
 * @param typeByColumn each column's {@code information_schema} data type, the parent column included
 */
record ChildTable(ChildCopy copy, Map<String, String> typeByColumn) {

    ChildTable {
        typeByColumn = Map.copyOf(typeByColumn);
    }

    static ChildTable read(JdbcTemplate jdbc, ChildCopy copy) {
        Map<String, String> types = TargetTable.columnTypes(jdbc, copy.table());
        if (!types.containsKey(copy.parentColumn())) {
            throw new IllegalStateException(copy.table() + " has no column " + copy.parentColumn());
        }
        return new ChildTable(copy, types);
    }

    String name() {
        return copy.table();
    }

    /** The columns a child row fills, the parent column left out: what a source entry and a stored row are compared on. */
    Set<String> valueColumns() {
        Set<String> columns = new TreeSet<>(typeByColumn.keySet());
        columns.remove(copy.parentColumn());
        return columns;
    }

    void insert(JdbcTemplate jdbc, String parentId, Map<String, Object> child) {
        List<String> columns = new ArrayList<>(child.keySet());
        List<Object> values = new ArrayList<>(child.values());
        columns.addFirst(copy.parentColumn());
        values.addFirst(parentId);
        jdbc.update("INSERT INTO " + TargetTable.quoted(name()) + " (" + columns.stream().map(TargetTable::quoted)
                .collect(Collectors.joining(", ")) + ") VALUES (" + columns.stream().map(column -> "?")
                .collect(Collectors.joining(", ")) + ")", values.toArray());
    }

    long count(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT count(*) FROM " + TargetTable.quoted(name()), Long.class);
    }

    /** Every stored child row, the parent column left out, grouped by its parent's key. */
    Map<String, List<Map<String, Object>>> rowsByParent(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        jdbc.query("SELECT * FROM " + TargetTable.quoted(name()), result -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : valueColumns()) {
                row.put(column, TargetTable.value(result, column, typeByColumn.get(column)));
            }
            rows.computeIfAbsent(result.getString(copy.parentColumn()), parent -> new ArrayList<>()).add(row);
        });
        return rows;
    }
}
