package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedColumn;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedTable;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.FieldLocation;
import com.thiru.wealthlens.taxplanning.documents.parser.PrintedText.Word;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;

/**
 * One table of a certificate read from its printed words (W-126c2b, D-737): its title, then each column heading by its own
 * words ({@link HeadingMatch}), then each row up to the Total row. An untitled table starts at its first heading, and a
 * table with no Total row is one row ending where the next table starts, as Form 16's Part A boxes are (W-126e3b, D-747). A column reaches halfway to its neighbours' headings, so
 * an amount set to the right of a wide cell is still under its heading (owner, in build); a value reaching under another
 * column's heading is not placed. A row starts with a value in the first column; an empty cell is no field (C4a).
 */
@RequiredArgsConstructor
final class TableReading {

    /** How many lines below its title a table's headings may print: a grouped header takes six in Part B. */
    private static final int HEADER_LINES = 8;

    private final List<Word> words;
    private final PrintedTable table;
    private final Set<Word> claimed;
    private final List<ExtractedField> fields = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private boolean complete = true;

    /**
     * What was read of one table.
     *
     * @param found whether its title, or an untitled table's first heading, is printed; a table not printed is noted and
     *     leaves the reading complete (C3a)
     * @param complete whether every heading and the Total row were found and every value placed and read
     * @param end the index after the table's last word, or the start where it is not printed
     */
    record Reading(boolean found, List<ExtractedField> fields, List<String> notes, boolean complete, int end) {

        Reading {
            fields = List.copyOf(fields);
            notes = List.copyOf(notes);
        }
    }

    /** A column's reach on the page: from halfway to the heading before to halfway to the one after. */
    private record Column(String key, String kind, List<String> captions, float headingLeft, float headingRight, float from, float to) {

        boolean holds(Word word) {
            float centre = (word.left() + word.right()) / 2;
            return centre >= from && centre < to;
        }

        boolean isUnderHeading(Word word) {
            return word.right() > headingLeft && word.left() < headingRight;
        }
    }

    /** @param next the table printed after this one, where a one-row table's row ends */
    static Reading read(List<Word> words, int from, PrintedTable table, Optional<PrintedTable> next, Set<Word> claimed) {
        return new TableReading(words, table, claimed).read(from, next);
    }

    private Reading read(int from, Optional<PrintedTable> next) {
        boolean untitled = table.title().isEmpty();
        OptionalInt headerFrom = untitled ? start(table, from) : title(from);
        if (headerFrom.isEmpty()) {
            String name = untitled ? table.columns().get(0).heading() : table.title().get(0);
            return new Reading(false, List.of(), List.of(name + " is not in this certificate"), true, from);
        }
        int to = headerFrom.getAsInt();
        int lastTitleLine = untitled ? words.get(to).line() - 1 : words.get(to - 1).line();
        while (to < words.size() && words.get(to).line() <= lastTitleLine + HEADER_LINES) {
            to++;
        }
        List<Column> columns = columns(headerFrom.getAsInt(), to);
        if (table.totalLabel().isEmpty()) {
            return oneRow(headerFrom.getAsInt(), next, columns);
        }
        int rowsFrom = endOfLine(lastClaimed(headerFrom.getAsInt(), to));
        Optional<List<Word>> total = total(rowsFrom, columns);
        if (total.isEmpty()) {
            notes.add("Not found: " + table.path() + ".total (" + table.totalLabel().orElseThrow() + ")");
            return new Reading(true, fields, notes, false, rowsFrom);
        }
        rows(rowsFrom, words.indexOf(total.get().get(0)), columns);
        int end = totalRow(total.get(), columns);
        return new Reading(true, fields, notes, complete, end);
    }

    /** Where an untitled table starts: its first column's heading, the first word of it. */
    private OptionalInt start(PrintedTable printed, int from) {
        return HeadingMatch.find(words, from, words.size(), printed.title().isEmpty() ? printed.columns().get(0).heading()
                : printed.title().get(0), claimed).map(found -> OptionalInt.of(words.indexOf(found.get(0)))).orElse(OptionalInt.empty());
    }

    /**
     * A table of one row, such as a row of Form 16's Part A boxes: every word between its first heading and the next table
     * that no heading claims, each under its column; the row ends where the next table starts.
     */
    private Reading oneRow(int from, Optional<PrintedTable> next, List<Column> columns) {
        OptionalInt end = next.map(after -> start(after, from)).orElse(OptionalInt.empty());
        if (end.isEmpty()) {
            missing("Not found: where " + table.path() + "'s row ends (" + next.map(PrintedTable::path).orElse("no table follows") + ")");
            return new Reading(true, fields, notes, false, from);
        }
        Map<String, List<Word>> cells = new LinkedHashMap<>();
        place(words.subList(from, end.getAsInt()).stream().filter(word -> !claimed.contains(word)).toList(), columns,
                table.path() + ".row1", cells);
        emit(table.path() + ".row1", cells, columns);
        return new Reading(true, fields, notes, complete, end.getAsInt());
    }

    /** The title's lines, in order; the index after the last, or none where a line is not printed. */
    private OptionalInt title(int from) {
        int at = from;
        for (String line : table.title()) {
            Optional<List<Word>> printed = HeadingMatch.find(words, at, words.size(), line, claimed);
            if (printed.isEmpty()) {
                return OptionalInt.empty();
            }
            claimed.addAll(printed.get());
            at = words.indexOf(printed.get().get(printed.get().size() - 1)) + 1;
        }
        return OptionalInt.of(at);
    }

    /** Each column found by its heading, then each group heading; a heading not printed is named and its column dropped. */
    private List<Column> columns(int from, int to) {
        List<PrintedColumn> printed = new ArrayList<>();
        List<List<Word>> headings = new ArrayList<>();
        for (PrintedColumn column : table.columns()) {
            Optional<List<Word>> heading = HeadingMatch.find(words, from, to, column.heading(), claimed);
            heading.ifPresentOrElse(found -> {
                claimed.addAll(found);
                printed.add(column);
                headings.add(found);
            }, () -> missing("Heading not found: " + table.path() + " (" + column.heading() + ")"));
        }
        table.columns().stream().map(PrintedColumn::group).flatMap(Optional::stream).distinct()
                .forEach(group -> HeadingMatch.find(words, from, to, group, claimed).ifPresentOrElse(claimed::addAll,
                        () -> missing("Heading not found: " + table.path() + " (" + group + ")")));
        return IntStream.range(0, printed.size()).mapToObj(at -> column(printed.get(at), headings, at)).toList();
    }

    private static Column column(PrintedColumn column, List<List<Word>> headings, int at) {
        float left = left(headings.get(at));
        float right = right(headings.get(at));
        float from = at == 0 ? Float.NEGATIVE_INFINITY : (right(headings.get(at - 1)) + left) / 2;
        float to = at == headings.size() - 1 ? Float.POSITIVE_INFINITY : (right + left(headings.get(at + 1))) / 2;
        return new Column(column.key(), column.kind(), column.captions(), left, right, from, to);
    }

    private static float left(List<Word> words) {
        return (float) words.stream().mapToDouble(Word::left).min().orElseThrow();
    }

    private static float right(List<Word> words) {
        return (float) words.stream().mapToDouble(Word::right).max().orElseThrow();
    }

    /** The last claimed word between {@code from} and {@code to}: the end of the header. */
    private int lastClaimed(int from, int to) {
        int last = from - 1;
        for (int at = from; at < to; at++) {
            if (claimed.contains(words.get(at))) {
                last = at;
            }
        }
        return last;
    }

    private int endOfLine(int at) {
        int end = at + 1;
        while (end < words.size() && words.get(end).line() == words.get(at).line()) {
            end++;
        }
        return end;
    }

    /**
     * The Total label's words in order: the first starting a line in the first column, the rest after it in that column,
     * on its line or the next, as a label wraps in a narrow cell ("Total ( Rs.)", Form 16 p.29).
     */
    private Optional<List<Word>> total(int from, List<Column> columns) {
        String[] label = table.totalLabel().orElseThrow().trim().split("\\s+");
        for (int at = from; at < words.size(); at++) {
            Word word = words.get(at);
            boolean startsLine = at == 0 || words.get(at - 1).line() != word.line();
            if (startsLine && inFirstColumn(word, columns) && !claimed.contains(word) && word.text().equals(label[0])) {
                Optional<List<Word>> printed = labelFrom(at, label, columns);
                if (printed.isPresent()) {
                    return printed;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<List<Word>> labelFrom(int at, String[] label, List<Column> columns) {
        List<Word> printed = new ArrayList<>(List.of(words.get(at)));
        int lastLine = words.get(at).line() + 1;
        for (int next = at + 1; next < words.size() && printed.size() < label.length && words.get(next).line() <= lastLine; next++) {
            Word word = words.get(next);
            if (inFirstColumn(word, columns)) {
                if (!word.text().equals(label[printed.size()])) {
                    return Optional.empty();
                }
                printed.add(word);
            }
        }
        return printed.size() == label.length ? Optional.of(printed) : Optional.empty();
    }

    private static boolean inFirstColumn(Word word, List<Column> columns) {
        return columns.isEmpty() || columns.get(0).holds(word);
    }

    /** The rows between the header and the Total row; a line with no first-column value continues the row above. */
    private void rows(int from, int to, List<Column> columns) {
        Map<String, List<Word>> cells = new LinkedHashMap<>();
        int row = 0;
        for (int at = from; at < to; at = endOfLine(at)) {
            List<Word> line = words.subList(at, Math.min(endOfLine(at), to));
            if (row == 0 || !columns.isEmpty() && line.stream().anyMatch(columns.get(0)::holds)) {
                emit(table.path() + ".row" + row, cells, columns);
                cells.clear();
                row++;
            }
            place(line, columns, table.path() + ".row" + row, cells);
        }
        emit(table.path() + ".row" + row, cells, columns);
    }

    /** The Total row: each declared cell reaches over the columns it spans; the row ends after its label's last word. */
    private int totalRow(List<Word> label, List<Column> columns) {
        claimed.addAll(label);
        int total = words.indexOf(label.get(0));
        List<Column> cells = new ArrayList<>();
        for (List<String> keys : table.totalCells()) {
            List<Column> spanned = columns.stream().filter(column -> keys.contains(column.key())).toList();
            if (!spanned.isEmpty()) {
                Column first = spanned.get(0);
                Column last = spanned.get(spanned.size() - 1);
                cells.add(new Column(first.key(), first.kind(), List.of(), first.headingLeft(), last.headingRight(), first.from(), last.to()));
            }
        }
        int end = endOfLine(total);
        Map<String, List<Word>> printed = new LinkedHashMap<>();
        place(words.subList(total + 1, end).stream().filter(word -> !label.contains(word)).toList(), cells, table.path() + ".total",
                printed);
        emit(table.path() + ".total", printed, cells);
        return Math.max(end, words.indexOf(label.get(label.size() - 1)) + 1);
    }

    /** Each word to the column whose reach holds its centre, unless it reaches under another column's heading. */
    private void place(List<Word> line, List<Column> columns, String row, Map<String, List<Word>> cells) {
        for (Word word : line) {
            claimed.add(word);
            Optional<Column> column = columns.stream().filter(candidate -> candidate.holds(word)).findFirst();
            boolean spills = column.isPresent()
                    && columns.stream().anyMatch(other -> other != column.get() && other.isUnderHeading(word));
            if (column.isEmpty() || spills) {
                missing("Not placed: " + row + " (" + word.text() + ")");
            } else {
                cells.computeIfAbsent(column.get().key(), key -> new ArrayList<>()).add(word);
            }
        }
    }

    /** One field per printed cell, read by its column's kind (L2); a cell not read keeps its text and is named. */
    private void emit(String row, Map<String, List<Word>> cells, List<Column> columns) {
        for (Column column : columns) {
            List<Word> cell = cells.getOrDefault(column.key(), List.of());
            if (!cell.isEmpty() && !column.captions().isEmpty()) {
                captioned(row + "." + column.key(), cell, column.captions()).forEach((path, words) -> emit(path, words, column.kind()));
            } else if (!cell.isEmpty()) {
                emit(row + "." + column.key(), cell, column.kind());
            }
        }
    }

    private void emit(String path, List<Word> cell, String kind) {
        String text = cell.stream().map(Word::text).collect(Collectors.joining(" "));
        Optional<ExtractedValue> value = switch (kind) {
            case "AMOUNT" -> CellReading.amounts(text, 1).map(values -> values.get(0));
            case "YES_NO" -> CellReading.yesNo(text);
            default -> Optional.of(new ExtractedValue.TextRead(text));
        };
        if (value.isEmpty()) {
            missing("Not read: " + path + " (" + text + ")");
        }
        fields.add(new ExtractedField(path, Optional.of(text), value,
                value.isPresent() ? CertificateReading.READ_CONFIDENCE : CertificateReading.LABEL_CONFIDENCE, Optional.of(place(cell)),
                Optional.empty()));
    }

    /**
     * A cell whose column prints captions (Form 16's "CIT (TDS)": Address, City, Pin Code): each caption's words claimed, the
     * words after it its value, by the caption's path; words before the first caption are not placed.
     */
    private Map<String, List<Word>> captioned(String path, List<Word> cell, List<String> captions) {
        Map<String, List<Word>> values = new LinkedHashMap<>();
        Optional<String> current = Optional.empty();
        for (int at = 0; at < cell.size(); at++) {
            Optional<String> caption = captionAt(cell, at, captions);
            if (caption.isPresent()) {
                current = caption;
                at += caption.get().split("\\s+").length - 1;
            } else if (current.isEmpty()) {
                missing("Not placed: " + path + " (" + cell.get(at).text() + ")");
            } else {
                values.computeIfAbsent(CertificateLayout.cellPath(path, current), key -> new ArrayList<>()).add(cell.get(at));
            }
        }
        return values;
    }

    private static Optional<String> captionAt(List<Word> cell, int at, List<String> captions) {
        return captions.stream().filter(caption -> {
            String[] tokens = caption.split("\\s+");
            return at + tokens.length <= cell.size() && IntStream.range(0, tokens.length)
                    .allMatch(i -> cell.get(at + i).text().equals(tokens[i]));
        }).findFirst();
    }

    private static FieldLocation place(List<Word> cell) {
        Word first = cell.get(0);
        List<Word> onPage = cell.stream().filter(word -> word.page() == first.page()).toList();
        float top = (float) onPage.stream().mapToDouble(Word::top).min().orElseThrow();
        float bottom = (float) onPage.stream().mapToDouble(Word::bottom).max().orElseThrow();
        return new FieldLocation(first.page(), left(onPage), top, right(onPage) - left(onPage), bottom - top);
    }

    private void missing(String note) {
        notes.add(note);
        complete = false;
    }
}
