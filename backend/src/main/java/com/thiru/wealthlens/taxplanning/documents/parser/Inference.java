package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedSum;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.SumCell;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * L3 ({@code DR 06} G5.1; W-127b): where one of a printed sum's cells is unknown and every other is read, that cell is what the
 * sum makes it — and nowhere else. A cell is unknown when nothing is printed in it or its line was not found; a cell printing
 * text L2 could not read keeps that text (D-734 C2a). A sum whose cells are all read and disagree is left for W-128.
 */
final class Inference {

    /** {@code DR 06} G5.1 L3's 0.70 – 0.85, its middle (D-732 C3a). */
    static final double INFERRED_CONFIDENCE = 0.80;

    private Inference() {
    }

    /** The fields with every cell the sums determine filled in, and a note for each (D-734 C3a). */
    record Filled(List<ExtractedField> fields, List<String> notes) {
    }

    static Filled fill(List<ExtractedField> fields, List<PrintedSum> sums) {
        Map<String, ExtractedField> byPath = new LinkedHashMap<>();
        fields.forEach(field -> byPath.put(field.fieldPath(), field));
        List<String> notes = new ArrayList<>();
        boolean filledOne = true;
        while (filledOne) {
            filledOne = false;
            for (PrintedSum sum : sums) {
                Optional<ExtractedField> inferred = solve(sum, byPath);
                if (inferred.isPresent()) {
                    byPath.put(inferred.get().fieldPath(), inferred.get());
                    notes.add("Inferred " + inferred.get().fieldPath() + " from the sum printed for " + sum.target().path());
                    filledOne = true;
                }
            }
        }
        return new Filled(List.copyOf(byPath.values()), notes);
    }

    /** The one unknown cell of {@code sum}, solved; empty unless exactly one is unknown and the rest are read. */
    private static Optional<ExtractedField> solve(PrintedSum sum, Map<String, ExtractedField> byPath) {
        List<SumCell> cells = Stream.concat(Stream.of(sum.target()), sum.terms().stream()).toList();
        List<SumCell> unknown = new ArrayList<>();
        Money balance = Money.ZERO;
        for (SumCell cell : cells) {
            Optional<Money> read = amount(byPath.get(cell.path()));
            if (read.isPresent()) {
                balance = cell == sum.target() ? balance.plus(read.get()) : balance.minus(read.get().times(cell.sign()));
            } else if (fillable(cell, byPath)) {
                unknown.add(cell);
            } else {
                return Optional.empty();
            }
        }
        if (unknown.size() != 1) {
            return Optional.empty();
        }
        // target - sum(sign * term) = 0: an unknown target is -balance; an unknown term is sign * balance
        SumCell cell = unknown.get(0);
        Money value = cell == sum.target() ? Money.ZERO.minus(balance) : balance.times(cell.sign());
        ExtractedField was = byPath.get(cell.path());
        return Optional.of(new ExtractedField(cell.path(), Optional.empty(), Optional.of(new ExtractedValue.MoneyRead(value)),
                INFERRED_CONFIDENCE, was == null ? Optional.empty() : was.location(), Optional.empty()));
    }

    private static Optional<Money> amount(ExtractedField field) {
        return Optional.ofNullable(field).flatMap(ExtractedField::interpretedValue)
                .filter(ExtractedValue.MoneyRead.class::isInstance).map(value -> ((ExtractedValue.MoneyRead) value).amount());
    }

    /** Nothing printed in the cell, or its line not found: never a cell printing text L2 could not read. */
    private static boolean fillable(SumCell cell, Map<String, ExtractedField> byPath) {
        ExtractedField own = byPath.get(cell.path());
        ExtractedField line = byPath.get(cell.line());
        return (own == null || own.rawText().isEmpty() && own.interpretedValue().isEmpty())
                && (line == null || line.rawText().isEmpty());
    }
}
