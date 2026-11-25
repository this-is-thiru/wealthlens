package com.thiru.wealthlens.shared.migration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A PostgreSQL table as the copier sees it (W-337g): its columns and their types, read from the database Flyway built rather
 * than restated, and its primary key, which a second run of the copier matches on (D-953 C3a).
 *
 * @param typeByColumn each column's {@code information_schema} data type
 */
record TargetTable(String name, String primaryKey, Map<String, String> typeByColumn) {

    TargetTable {
        typeByColumn = Map.copyOf(typeByColumn);
    }

    static TargetTable read(JdbcTemplate jdbc, String name) {
        Map<String, String> types = new HashMap<>();
        jdbc.query("SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = current_schema() "
                + "AND table_name = ?", row -> {
                    types.put(row.getString("column_name"), row.getString("data_type"));
                }, name);
        if (types.isEmpty()) {
            throw new IllegalStateException("The target has no table " + name);
        }
        List<String> keys = jdbc.queryForList("SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid "
                + "AND a.attnum = ANY(i.indkey) WHERE i.indrelid = ?::regclass AND i.indisprimary", String.class, name);
        if (keys.size() != 1) {
            throw new IllegalStateException(name + " has no single-column primary key");
        }
        return new TargetTable(name, keys.getFirst(), types);
    }

    /** Writes the row unless one with its key is there already: a second run changes nothing it wrote before. */
    void insert(JdbcTemplate jdbc, SourceRow row) {
        List<String> columns = List.copyOf(row.values().keySet());
        String sql = "INSERT INTO " + quoted(name) + " (" + columns.stream().map(TargetTable::quoted).collect(Collectors.joining(", "))
                + ") VALUES (" + columns.stream().map(column -> "?").collect(Collectors.joining(", ")) + ") ON CONFLICT ("
                + quoted(primaryKey) + ") DO NOTHING";
        jdbc.update(sql, columns.stream().map(row.values()::get).toArray());
    }

    Set<String> ids(JdbcTemplate jdbc) {
        return new HashSet<>(jdbc.queryForList("SELECT " + quoted(primaryKey) + " FROM " + quoted(name), String.class));
    }

    /** Every row by its key, each column read as the type the copier writes it as. */
    Map<String, Map<String, Object>> rows(JdbcTemplate jdbc) {
        Map<String, Map<String, Object>> rows = new HashMap<>();
        jdbc.query("SELECT * FROM " + quoted(name), result -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, String> column : typeByColumn.entrySet()) {
                row.put(column.getKey(), switch (column.getValue()) {
                    case "date" -> result.getObject(column.getKey(), LocalDate.class);
                    case "timestamp without time zone" -> result.getObject(column.getKey(), LocalDateTime.class);
                    case "timestamp with time zone" -> result.getObject(column.getKey(), OffsetDateTime.class);
                    default -> result.getObject(column.getKey());
                });
            }
            rows.put(result.getString(primaryKey), row);
        });
        return rows;
    }

    private static String quoted(String identifier) {
        return "\"" + identifier + "\"";
    }
}
