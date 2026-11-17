package com.thiru.wealthlens.taxplanning.documents.parser;

import com.itextpdf.text.exceptions.BadPasswordException;
import com.itextpdf.text.pdf.PdfReader;
import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PartHeading;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedLine;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedTable;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.FieldLocation;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.parser.LabelMatch.Found;
import com.thiru.wealthlens.taxplanning.documents.parser.PrintedText.Word;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

/**
 * Reads one certificate against its year's layout from the pack ({@code DR 06} G5.1): at L1 its parts by their headings and
 * every line by its printed label (W-126a, W-126b), and its tables by their titles and column headings (W-126c2b); at L2
 * each value cell by its kind (W-127a); at L3 a cell the form's printed sums determine (W-127b). Every certificate parser
 * shares it, so a form is its layout and a name, not a second reader (W-126e2). Every failure is a reading with its reason,
 * never an exception ({@code DR 07} X2.5).
 */
final class CertificateReading {

    /** {@code DR 06} G5.1, L1: a label matched exactly. */
    static final double LABEL_CONFIDENCE = 0.95;
    /** {@code DR 06} G5.1, L2: a cell read under a matched label — the middle of its 0.85 – 0.95 (D-732 C3a). */
    static final double READ_CONFIDENCE = 0.90;

    private CertificateReading() {
    }

    /**
     * @param layout the year's layout of the form, if the year prescribes it
     * @param form the form's name as a reason names it, e.g. "Form No. 130"
     */
    static DocumentReading read(DocumentContent content, TaxYearId year, Optional<CertificateLayout> layout, String form) {
        if (layout.isEmpty()) {
            return failed("No " + form + " layout is in force in " + year.label());
        }
        PdfReader reader;
        try {
            reader = new PdfReader(content.bytes());
        } catch (BadPasswordException e) {
            return failed("The PDF is protected by a password: upload it without one (a password is never kept)");
        } catch (IOException e) {
            return failed("Not a PDF this parser can read: " + e.getMessage());
        }
        try {
            List<Word> words = PrintedText.of(reader).words();
            return words.isEmpty() ? failed("The PDF has no text layer: a scanned page is not read yet")
                    : new Matching(layout.get(), words, form, year).read();
        } catch (IOException e) {
            return failed("The PDF's text could not be read: " + e.getMessage());
        } finally {
            reader.close();
        }
    }

    private static DocumentReading failed(String reason) {
        return new DocumentReading(ParseOutcome.FAILED, List.of(), List.of(), List.of(reason));
    }

    /** One certificate's words matched against the layout, in printed order, a cursor moving only forward. */
    @RequiredArgsConstructor
    private static final class Matching {

        private final CertificateLayout layout;
        private final List<Word> words;
        private final String form;
        private final TaxYearId year;
        private final Set<Word> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<String, Boolean> partsFound = new HashMap<>();
        private final Map<String, int[]> spans = new HashMap<>();
        private final List<ExtractedField> fields = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private int cursor;
        private boolean missing;

        DocumentReading read() {
            PartHeading first = layout.parts().get(0);
            if (!enter(first)) {
                return failed("Not a " + form + ": no " + first.heading());
            }
            layout.parts().stream().filter(this::isTop).forEach(this::readPart);
            List<String> unmapped = unmapped();
            ParseOutcome outcome = missing || !unmapped.isEmpty() ? ParseOutcome.PARTIAL : ParseOutcome.SUCCESS;
            Inference.Filled filled = Inference.fill(fields, layout.sums());
            notes.addAll(filled.notes());
            Contradictions.Checked checked = Contradictions.check(filled.fields(), layout.sums(), layout.tables(), layout.agreements(), year);
            notes.addAll(checked.contradictions());
            notes.addAll(checked.differences());
            return new DocumentReading(checked.contradictions().isEmpty() ? outcome : ParseOutcome.PARTIAL, checked.fields(), unmapped,
                    notes);
        }

        /** A top part in printed order: its lines, then its tables. */
        private void readPart(PartHeading top) {
            for (PrintedLine line : layout.lines()) {
                if (line.path().startsWith(top.path() + ".") && !line.repeating() && partsOf(line.path()).allMatch(this::entered)) {
                    match(line);
                }
            }
            List<PrintedTable> tables = layout.tables().stream().filter(table -> table.path().startsWith(top.path() + ".")).toList();
            if (!tables.isEmpty() && entered(top)) {
                for (int at = 0; at < tables.size(); at++) {
                    read(tables.get(at), at + 1 < tables.size() ? Optional.of(tables.get(at + 1)) : Optional.empty(), top);
                }
            }
        }

        private void read(PrintedTable table, Optional<PrintedTable> next, PartHeading top) {
            TableReading.Reading reading = TableReading.read(words, cursor, table, next, claimed);
            fields.addAll(reading.fields());
            notes.addAll(reading.notes());
            missing |= !reading.complete();
            if (reading.found()) {
                cursor = reading.end();
                spans.get(top.path())[1] = Math.max(spans.get(top.path())[1], reading.end());
            }
        }

        /** The parts a line sits in, outermost first. */
        private Stream<PartHeading> partsOf(String path) {
            return layout.parts().stream().filter(part -> path.startsWith(part.path() + "."))
                    .sorted((a, b) -> Integer.compare(a.path().length(), b.path().length()));
        }

        /** Whether the line's part is printed; a group heading not found is noted, and its lines are still looked for. */
        private boolean entered(PartHeading part) {
            if (!partsFound.containsKey(part.path())) {
                boolean found = enter(part);
                if (!found && isTop(part)) {
                    notes.add(part.heading() + " is not in this certificate");
                } else if (!found) {
                    notes.add("Heading not found: " + part.heading());
                    missing = true;
                }
            }
            return partsFound.get(part.path()) || !isTop(part);
        }

        private boolean enter(PartHeading part) {
            Optional<Found> found = LabelMatch.find(words, cursor, part.heading());
            partsFound.put(part.path(), found.isPresent());
            found.ifPresent(heading -> {
                claim(heading);
                if (isTop(part)) {
                    int at = words.indexOf(heading.label().get(0));
                    spans.put(part.path(), new int[] {at, heading.end()});
                }
            });
            return found.isPresent();
        }

        private boolean isTop(PartHeading part) {
            return layout.parts().stream().noneMatch(other -> part.path().startsWith(other.path() + "."));
        }

        private void match(PrintedLine line) {
            Optional<Found> found = LabelMatch.find(words, cursor, line.label());
            if (found.isEmpty()) {
                notes.add("Not found: " + line.path() + " (" + line.label() + ")");
                missing = true;
                return;
            }
            Found at = found.get();
            claim(at);
            partsOf(line.path()).filter(this::isTop).findFirst()
                    .ifPresent(top -> spans.get(top.path())[1] = Math.max(spans.get(top.path())[1], at.end()));
            boolean heading = "NONE".equals(line.kind());
            Optional<String> text = heading || at.values().isEmpty() ? Optional.empty()
                    : Optional.of(at.values().stream().map(Word::text).collect(Collectors.joining(" ")));
            fields.addAll(read(line, text, place(at)));
        }

        /** L2: the line's value text read by its kind (W-127a); a cell not read keeps its text and is named. */
        private List<ExtractedField> read(PrintedLine line, Optional<String> text, FieldLocation place) {
            if (text.isEmpty()) {
                return List.of(located(line.path(), text, Optional.empty(), LABEL_CONFIDENCE, place));
            }
            String printed = text.get();
            Optional<List<ExtractedValue>> values = switch (line.kind()) {
                case "AMOUNT" -> CellReading.amounts(printed, Math.max(1, line.columns().size()));
                case "YES_NO" -> CellReading.yesNo(printed).map(List::of);
                case "PERIOD" -> CellReading.period(printed).map(List::of);
                default -> Optional.of(List.of(new ExtractedValue.TextRead(printed)));
            };
            if (values.isEmpty()) {
                notes.add("Not read: " + line.path() + " (" + printed + ")");
                missing = true;
                return List.of(located(line.path(), text, Optional.empty(), LABEL_CONFIDENCE, place));
            }
            if (values.get().size() == 1) {
                return List.of(located(line.path(), text, Optional.of(values.get().get(0)), READ_CONFIDENCE, place));
            }
            String[] cells = printed.trim().split("\\s+");
            List<ExtractedField> columns = new ArrayList<>();
            for (int at = 0; at < cells.length; at++) {
                columns.add(located(CertificateLayout.cellPath(line.path(), Optional.of(line.columns().get(at))), Optional.of(cells[at]),
                        Optional.of(values.get().get(at)), READ_CONFIDENCE, place));
            }
            return columns;
        }

        private static ExtractedField located(String path, Optional<String> text, Optional<ExtractedValue> value,
                double confidence, FieldLocation place) {
            return new ExtractedField(path, text, value, confidence, Optional.of(place), Optional.empty());
        }

        private void claim(Found found) {
            claimed.addAll(found.label());
            claimed.addAll(found.values());
            cursor = found.end();
        }

        /** The box around the label and its value cells, on the label's first page. */
        private static FieldLocation place(Found found) {
            int page = found.label().get(0).page();
            List<Word> onPage = Stream.concat(found.label().stream(), found.values().stream())
                    .filter(word -> word.page() == page).toList();
            float left = (float) onPage.stream().mapToDouble(Word::left).min().orElseThrow();
            float right = (float) onPage.stream().mapToDouble(Word::right).max().orElseThrow();
            float top = (float) onPage.stream().mapToDouble(Word::top).min().orElseThrow();
            float bottom = (float) onPage.stream().mapToDouble(Word::bottom).max().orElseThrow();
            return new FieldLocation(page, left, top, right - left, bottom - top);
        }

        /**
         * Text inside a printed part that no heading, label or value claims, line by line (L4) — but not the column
         * headings the layout itself names, nor anything between parts, such as a declaration or its notes.
         */
        private List<String> unmapped() {
            Set<String> columnWords = layout.lines().stream().flatMap(line -> line.columns().stream())
                    .flatMap(column -> Stream.of(column.split("\\s+"))).collect(Collectors.toSet());
            Map<Integer, List<String>> byLine = new java.util.TreeMap<>();
            for (int[] span : spans.values()) {
                for (int at = span[0]; at < span[1]; at++) {
                    Word word = words.get(at);
                    if (!claimed.contains(word)) {
                        byLine.computeIfAbsent(word.line(), line -> new ArrayList<>()).add(word.text());
                    }
                }
            }
            return byLine.values().stream().filter(line -> !columnWords.containsAll(line))
                    .map(line -> String.join(" ", line)).toList();
        }
    }
}
