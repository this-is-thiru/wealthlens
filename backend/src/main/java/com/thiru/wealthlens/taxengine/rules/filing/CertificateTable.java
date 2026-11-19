package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A table a certificate prints, whose rows the issuer writes (W-126c2a, D-737 C2a): Form No. 130's Part B prints a row
 * per quarter or per deposit with no label of its own, so a row is found under its table's title and column headings,
 * never by a label. Law as data, versioned with its form.
 *
 * @param path dot-separated, under its part's path, e.g. {@code partB.summary}
 * @param title the lines printed over the table, in order; none where the table is found by its first heading, as Form 16's
 *     Part A boxes are (W-126e3a, D-745 C1a)
 * @param columns in printed order
 * @param total the row that prints the totals; none for a table of one row, such as a row of Part A's boxes
 * @param page the Gazette page the table starts on
 */
public record CertificateTable(String path, List<String> title, List<TableColumn> columns, Optional<TableTotal> total,
        int page) {

    public CertificateTable {
        if (path == null || !CertificatePart.PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("A certificate table's path is dot-separated words: " + path);
        }
        if (title == null || title.stream().anyMatch(line -> line == null || line.isBlank())
                || columns == null || columns.isEmpty() || total == null || page < 1) {
            throw new IllegalArgumentException(path + " needs its printed title or none, its columns, its total row or none and its page");
        }
        title = List.copyOf(title);
        columns = List.copyOf(columns);
        List<String> keys = keys(path, columns);
        requireGroupsSpanNeighbours(path, columns);
        total.ifPresent(row -> requireTotalCellsFit(path, keys, row));
    }

    private static List<String> keys(String path, List<TableColumn> columns) {
        List<String> keys = columns.stream().map(TableColumn::key).toList();
        if (new HashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException(path + " names a column key twice: " + keys);
        }
        return keys;
    }

    /** A group heading is printed over two or more neighbouring columns. */
    private static void requireGroupsSpanNeighbours(String path, List<TableColumn> columns) {
        Set<String> closed = new HashSet<>();
        Optional<String> open = Optional.empty();
        int width = 0;
        for (TableColumn column : columns) {
            if (!column.group().equals(open)) {
                requireWide(path, open, width);
                open.ifPresent(closed::add);
                if (column.group().filter(closed::contains).isPresent()) {
                    throw new IllegalArgumentException(path + "'s group " + column.group().get() + " is printed over columns apart");
                }
                open = column.group();
                width = 0;
            }
            width++;
        }
        requireWide(path, open, width);
    }

    private static void requireWide(String path, Optional<String> group, int width) {
        if (group.isPresent() && width < 2) {
            throw new IllegalArgumentException(path + "'s group " + group.get() + " is printed over one column; a heading is enough");
        }
    }

    /** Each total cell spans known columns, side by side, and no column is in two cells. */
    private static void requireTotalCellsFit(String path, List<String> keys, TableTotal total) {
        Set<String> used = new HashSet<>();
        for (List<String> cell : total.cells()) {
            for (int at = 0; at < cell.size(); at++) {
                String key = cell.get(at);
                if (!keys.contains(key) || !used.add(key)) {
                    throw new IllegalArgumentException(path + "'s total names column " + key + ", which it lacks or names twice");
                }
                if (at > 0 && keys.indexOf(key) != keys.indexOf(cell.get(at - 1)) + 1) {
                    throw new IllegalArgumentException(path + "'s total merges " + cell + ", columns that are not side by side");
                }
            }
        }
    }
}
