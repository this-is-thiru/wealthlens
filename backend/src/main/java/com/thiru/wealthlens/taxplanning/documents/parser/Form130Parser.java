package com.thiru.wealthlens.taxplanning.documents.parser;

import com.itextpdf.text.exceptions.BadPasswordException;
import com.itextpdf.text.pdf.PdfReader;
import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PartHeading;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedLine;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads a Form No. 130 ({@code DR 06} G5.1): at L1 its parts by their headings and every line by its printed label, from the
 * year's layout in the pack (W-126a, W-126b); at L2 each line's value cells by its kind (W-127a). Every failure is a reading
 * with its reason, never an exception ({@code DR 07} X2.5).
 */
@Component
@RequiredArgsConstructor
public class Form130Parser implements DocumentParser {

    /** {@code DR 06} G5.1, L1: a label matched exactly. */
    static final double LABEL_CONFIDENCE = 0.95;
    /** {@code DR 06} G5.1, L2: a cell read under a matched label — the middle of its 0.85 – 0.95 (D-732 C3a). */
    static final double READ_CONFIDENCE = 0.90;

    private final TaxCalculator taxCalculator;

    @Override
    public DocumentType reads() {
        return DocumentType.FORM_130;
    }

    @Override
    public String id() {
        return "form-130";
    }

    @Override
    public String version() {
        return "2";
    }

    /** @throws IllegalArgumentException where no pack ships for {@code year} — the upload refuses such a year first */
    @Override
    public DocumentReading read(DocumentContent content, TaxYearId year) {
        Optional<CertificateLayout> layout = taxCalculator.certificate(year, new CertificateCode(reads().name()));
        if (layout.isEmpty()) {
            return failed("No Form No. 130 layout is in force in " + year.label());
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
                    : new Matching(layout.get(), words).read();
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
                return failed("Not a Form No. 130: no " + first.heading());
            }
            for (PrintedLine line : layout.lines()) {
                if (!line.repeating() && partsOf(line.path()).allMatch(this::entered)) {
                    match(line);
                }
            }
            List<String> unmapped = unmapped();
            ParseOutcome outcome = missing || !unmapped.isEmpty() ? ParseOutcome.PARTIAL : ParseOutcome.SUCCESS;
            return new DocumentReading(outcome, fields, unmapped, notes);
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
                columns.add(located(line.path() + "." + key(line.columns().get(at)), Optional.of(cells[at]),
                        Optional.of(values.get().get(at)), READ_CONFIDENCE, place));
            }
            return columns;
        }

        private static ExtractedField located(String path, Optional<String> text, Optional<ExtractedValue> value,
                double confidence, FieldLocation place) {
            return new ExtractedField(path, text, value, confidence, Optional.of(place), Optional.empty());
        }

        /** A column's printed heading as a path word: "Gross Amount" is {@code grossAmount}. */
        private static String key(String heading) {
            String[] words = heading.trim().split("\\s+");
            StringBuilder key = new StringBuilder(words[0].toLowerCase(Locale.ROOT));
            for (int at = 1; at < words.length; at++) {
                key.append(Character.toUpperCase(words[at].charAt(0))).append(words[at].substring(1).toLowerCase(Locale.ROOT));
            }
            return key.toString();
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
         * headings the layout itself names, nor anything between parts, such as Part B (W-126c).
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
