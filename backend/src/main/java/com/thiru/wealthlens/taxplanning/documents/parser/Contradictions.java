package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedSum;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedTable;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.SumCell;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A certificate checked against its own arithmetic ({@code DR 06} G5.2; W-128a, D-750): a sum the form prints whose cells are
 * all read must hold exactly, and a table's Total amount must be the sum of its rows. A contradiction is never resolved by
 * picking a side: it is named with both figures, and every cell of it drops to {@link #CONTRADICTED_CONFIDENCE}.
 */
final class Contradictions {

    /** Below L3's 0.70 floor, so a contradicted cell is never trusted above an inferred one (D-750 C2a, adopted by the owner). */
    static final double CONTRADICTED_CONFIDENCE = 0.50;

    private Contradictions() {
    }

    /** The fields, contradicted cells lowered, and a note for each contradiction. */
    record Checked(List<ExtractedField> fields, List<String> notes) {
    }

    static Checked check(List<ExtractedField> fields, List<PrintedSum> sums, List<PrintedTable> tables) {
        Map<String, ExtractedField> byPath = new LinkedHashMap<>();
        fields.forEach(field -> byPath.put(field.fieldPath(), field));
        List<String> notes = new ArrayList<>();
        sums.forEach(sum -> checkSum(sum, byPath, notes));
        tables.forEach(table -> table.totalCells().forEach(cell -> checkTotal(table, cell.get(0), byPath, notes)));
        return new Checked(List.copyOf(byPath.values()), notes);
    }

    /** Exactly (D-750 C4a), and only when every cell is read: an inferred cell agrees by construction. */
    private static void checkSum(PrintedSum sum, Map<String, ExtractedField> byPath, List<String> notes) {
        List<SumCell> cells = Stream.concat(Stream.of(sum.target()), sum.terms().stream()).toList();
        if (!cells.stream().allMatch(cell -> amount(byPath.get(cell.path())).isPresent())) {
            return;
        }
        Money printed = amount(byPath.get(sum.target().path())).orElseThrow();
        Money gives = sum.terms().stream().map(term -> amount(byPath.get(term.path())).orElseThrow().times(term.sign()))
                .reduce(Money.ZERO, Money::plus);
        if (printed.compareTo(gives) != 0) {
            notes.add("Contradiction: " + sum.target().path() + " printed " + printed.inIndianNotation()
                    + ", the sum printed for it gives " + gives.inIndianNotation());
            cells.forEach(cell -> lower(byPath, cell.path()));
        }
    }

    /**
     * A Total cell whose first column is an amount (C2a), against that column's rows: a blank cell is nothing, a cell
     * printing text L2 could not read leaves the column unchecked (C1a).
     */
    private static void checkTotal(PrintedTable table, String key, Map<String, ExtractedField> byPath, List<String> notes) {
        boolean isAmount = table.columns().stream().anyMatch(column -> column.key().equals(key) && "AMOUNT".equals(column.kind()));
        String totalPath = table.path() + ".total." + key;
        Optional<Money> total = amount(byPath.get(totalPath));
        Pattern row = Pattern.compile(Pattern.quote(table.path()) + "\\.row\\d+\\." + Pattern.quote(key));
        List<String> rows = byPath.keySet().stream().filter(path -> row.matcher(path).matches()).toList();
        if (!isAmount || total.isEmpty() || rows.stream().anyMatch(path -> amount(byPath.get(path)).isEmpty())) {
            return;
        }
        Money added = rows.stream().map(path -> amount(byPath.get(path)).orElseThrow()).reduce(Money.ZERO, Money::plus);
        if (total.get().compareTo(added) != 0) {
            notes.add("Contradiction: " + totalPath + " printed " + total.get().inIndianNotation() + ", its rows add to "
                    + added.inIndianNotation());
            lower(byPath, totalPath);
            rows.forEach(path -> lower(byPath, path));
        }
    }

    private static void lower(Map<String, ExtractedField> byPath, String path) {
        ExtractedField field = byPath.get(path);
        byPath.put(path, new ExtractedField(field.fieldPath(), field.rawText(), field.interpretedValue(), CONTRADICTED_CONFIDENCE,
                field.location(), field.mappedTo()));
    }

    private static Optional<Money> amount(ExtractedField field) {
        return Optional.ofNullable(field).flatMap(ExtractedField::interpretedValue)
                .filter(ExtractedValue.MoneyRead.class::isInstance).map(value -> ((ExtractedValue.MoneyRead) value).amount());
    }
}
