package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedColumn;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedTable;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.parser.PrintedText.Word;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * One table read from its words (W-126c2b, D-737 C3a, C4a): a column reaches halfway to its neighbours' headings (owner, in
 * build), so an amount set to the right of a wide cell is still under its heading; a value reaching under another column's
 * heading is not read. Places in points.
 */
class TableReadingTest {

    private static final PrintedTable TABLE = new PrintedTable("partB.challan", List.of("II. Challan"),
            List.of(new PrintedColumn("Sl. No.", "serialNumber", "TEXT", Optional.empty(), List.of()),
                    new PrintedColumn("Tax deposited", "taxDeposited", "AMOUNT", Optional.empty(), List.of()),
                    new PrintedColumn("BSR Code", "bsrCode", "TEXT", Optional.empty(), List.of())),
            Optional.of("Total"), List.of(List.of("taxDeposited"), List.of("bsrCode")), 1);

    private static Word word(int line, String text, float left, float right) {
        return new Word(1, line, text, left, right, line * 12f, line * 12f + 10);
    }

    // Headings: "Sl. No." 58 – 86, "Tax deposited" 141 – 197, "BSR Code" 300 – 340; so the columns meet at 113.5 and 248.5
    private static List<Word> table(Word... rows) {
        List<Word> words = new java.util.ArrayList<>(List.of(word(0, "II.", 100, 110), word(0, "Challan", 114, 150),
                word(1, "Sl.", 58, 70), word(1, "No.", 74, 86), word(1, "Tax", 141, 154), word(1, "deposited", 158, 197),
                word(1, "BSR", 300, 318), word(1, "Code", 322, 340)));
        words.addAll(List.of(rows));
        return words;
    }

    private static TableReading.Reading read(List<Word> words) {
        return TableReading.read(words, 0, TABLE, Optional.empty(), Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Map<String, ExtractedField> byPath(TableReading.Reading reading) {
        return reading.fields().stream().collect(Collectors.toMap(ExtractedField::fieldPath, Function.identity()));
    }

    @Test
    void read_whenAnAmountIsSetRightInAWideCell_readsItUnderItsHeading() {
        // Given — 30,000.00 at 205 – 246: its centre 225.5 is past "deposited" (197) but short of the 248.5 midpoint
        TableReading.Reading reading = read(table(word(2, "1", 70, 75), word(2, "30,000.00", 205, 246), word(2, "0510308", 302, 338),
                word(3, "Total", 62, 83), word(3, "30,000.00", 205, 246)));

        // When
        Map<String, ExtractedField> fields = byPath(reading);

        // Then
        assertThat(reading.complete()).isTrue();
        assertThat(reading.notes()).isEmpty();
        assertThat(fields.get("partB.challan.row1.taxDeposited").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("30000")));
        assertThat(fields.get("partB.challan.row1.taxDeposited").confidence()).isEqualTo(0.90);
        assertThat(fields.get("partB.challan.row1.bsrCode").rawText()).contains("0510308");
        assertThat(fields.get("partB.challan.total.taxDeposited").rawText()).contains("30,000.00");
        assertThat(fields).doesNotContainKey("partB.challan.total.bsrCode");
    }

    @Test
    void read_whenAValueReachesUnderAnotherHeading_leavesItUnreadAndSaysSo() {
        // Given — "30,000.00" at 150 – 310: its centre (230) is in the tax column, but it reaches "BSR Code" (300 – 340)
        TableReading.Reading reading = read(table(word(2, "1", 70, 75), word(2, "30,000.00", 150, 310), word(3, "Total", 62, 83)));

        // Then
        assertThat(reading.complete()).isFalse();
        assertThat(reading.notes()).containsExactly("Not placed: partB.challan.row1 (30,000.00)");
        assertThat(byPath(reading)).doesNotContainKey("partB.challan.row1.taxDeposited");
    }

    @Test
    void read_whenALineHasNoFirstColumnValue_continuesTheRowAbove() {
        // Given — a BSR code printed on the line below its row
        TableReading.Reading reading = read(table(word(2, "1", 70, 75), word(2, "30,000.00", 205, 246), word(3, "0510308", 302, 338),
                word(4, "2", 70, 75), word(4, "10.00", 220, 246), word(5, "Total", 62, 83)));

        // When
        Map<String, ExtractedField> fields = byPath(reading);

        // Then
        assertThat(fields.get("partB.challan.row1.bsrCode").rawText()).contains("0510308");
        assertThat(fields.get("partB.challan.row2.serialNumber").rawText()).contains("2");
        assertThat(fields).doesNotContainKey("partB.challan.row2.bsrCode");
    }

    @Test
    void read_whenTheTotalRowIsMissing_isIncompleteAndSaysSo() {
        // When
        TableReading.Reading reading = read(table(word(2, "1", 70, 75)));

        // Then
        assertThat(reading.found()).isTrue();
        assertThat(reading.complete()).isFalse();
        assertThat(reading.notes()).containsExactly("Not found: partB.challan.total (Total)");
    }

    @Test
    void read_whenTheTitleIsNotPrinted_isNotFoundAndSaysTheTableIsAbsent() {
        // When
        TableReading.Reading reading = read(List.of(word(0, "DECLARATION", 100, 160)));

        // Then — C3a: item I or item II may be left out (Notes 3, 4)
        assertThat(reading.found()).isFalse();
        assertThat(reading.complete()).isTrue();
        assertThat(reading.notes()).containsExactly("II. Challan is not in this certificate");
        assertThat(reading.end()).isZero();
    }

    @Test
    void read_whenAColumnHeadingIsMissing_isIncompleteAndNamesIt() {
        // Given — no "BSR Code" printed
        List<Word> words = new java.util.ArrayList<>(table(word(2, "1", 70, 75), word(3, "Total", 62, 83)));
        words.removeIf(word -> word.text().equals("BSR") || word.text().equals("Code"));

        // When
        TableReading.Reading reading = read(words);

        // Then
        assertThat(reading.complete()).isFalse();
        assertThat(reading.notes()).containsExactly("Heading not found: partB.challan (BSR Code)");
    }

    private static final PrintedTable RUPEES = new PrintedTable("partA.challan", List.of("II. Challan"), TABLE.columns(),
            Optional.of("Total ( Rs.)"), List.of(List.of("taxDeposited"), List.of("bsrCode")), 29);

    @Test
    void read_whenTheTotalLabelIsSeveralWords_readsTheTotalRow() {
        // Given — Form 16's "Total ( Rs.)" (p.29) on one line, its amount in the tax column
        TableReading.Reading reading = TableReading.read(table(word(2, "1", 70, 75), word(3, "Total", 58, 80), word(3, "(", 82, 85),
                word(3, "Rs.)", 87, 100), word(3, "10,000.00", 205, 246)), 0, RUPEES, Optional.empty(), Collections.newSetFromMap(new IdentityHashMap<>()));

        // Then
        assertThat(reading.complete()).isTrue();
        assertThat(reading.notes()).isEmpty();
        assertThat(byPath(reading).get("partA.challan.total.taxDeposited").rawText()).contains("10,000.00");
    }

    @Test
    void read_whenTheTotalLabelWrapsInItsCell_readsTheTotalRowAndClaimsTheWrappedWords() {
        // Given — "Total (" on one line and "Rs.)" below it, still in the first column; the amount on the first line
        List<Word> words = table(word(2, "1", 70, 75), word(3, "Total", 58, 80), word(3, "(", 82, 85),
                word(3, "10,000.00", 205, 246), word(4, "Rs.)", 58, 71));
        java.util.Set<Word> claimed = Collections.newSetFromMap(new IdentityHashMap<>());

        // When
        TableReading.Reading reading = TableReading.read(words, 0, RUPEES, Optional.empty(), claimed);

        // Then
        assertThat(reading.notes()).isEmpty();
        assertThat(byPath(reading).get("partA.challan.total.taxDeposited").rawText()).contains("10,000.00");
        assertThat(claimed).contains(words.get(words.size() - 1));
        assertThat(reading.end()).isEqualTo(words.size());
    }

    @Test
    void read_whenOnlyPartOfASeveralWordTotalLabelIsPrinted_saysTheTotalIsNotFound() {
        // Given — "Total" alone where the layout prints "Total ( Rs.)"
        TableReading.Reading reading = TableReading.read(table(word(2, "1", 70, 75), word(3, "Total", 58, 80)), 0, RUPEES, Optional.empty(),
                Collections.newSetFromMap(new IdentityHashMap<>()));

        // Then
        assertThat(reading.complete()).isFalse();
        assertThat(reading.notes()).containsExactly("Not found: partA.challan.total (Total ( Rs.))");
    }

    @Test
    void read_whenALineStartsWithTotalButGoesOnDifferently_isNotTheTotalRow() {
        // Given — "Total ( Paise)" in the first column where the layout prints "Total ( Rs.)"
        TableReading.Reading reading = TableReading.read(table(word(2, "1", 70, 75), word(3, "Total", 58, 80), word(3, "(", 82, 85),
                word(3, "Paise)", 87, 105)), 0, RUPEES, Optional.empty(), Collections.newSetFromMap(new IdentityHashMap<>()));

        // Then
        assertThat(reading.notes()).containsExactly("Not found: partA.challan.total (Total ( Rs.))");
    }

    // A row of boxes: "Certificate No." 58 – 120 and "Last updated on" 300 – 380, values below; then a titled table
    private static final PrintedTable BOXES = new PrintedTable("partA.certificate", List.of(),
            List.of(new PrintedColumn("Certificate No.", "certificateNo", "TEXT", Optional.empty(), List.of()),
                    new PrintedColumn("Last updated on", "lastUpdatedOn", "TEXT", Optional.empty(), List.of())),
            Optional.empty(), List.of(), 28);
    private static final PrintedTable SUMMARY = new PrintedTable("partA.summary", List.of("Summary"), TABLE.columns(),
            Optional.of("Total"), List.of(List.of("taxDeposited")), 28);

    private static List<Word> boxes(Word... after) {
        List<Word> words = new java.util.ArrayList<>(List.of(word(0, "Certificate", 58, 100), word(0, "No.", 104, 120),
                word(0, "Last", 300, 320), word(0, "updated", 324, 360), word(0, "on", 364, 380),
                word(1, "QWERTYU", 60, 110), word(1, "01-Jun-2026", 305, 370)));
        words.addAll(List.of(after));
        return words;
    }

    @Test
    void read_whenAnUntitledOneRowTableIsFollowedByATable_readsItsRowUpToThatTablesTitle() {
        // Given — the summary's title starts on line 2
        List<Word> words = boxes(word(2, "Summary", 58, 110));

        // When
        TableReading.Reading reading = TableReading.read(words, 0, BOXES, Optional.of(SUMMARY),
                Collections.newSetFromMap(new IdentityHashMap<>()));

        // Then
        assertThat(reading.complete()).isTrue();
        assertThat(reading.notes()).isEmpty();
        assertThat(byPath(reading).get("partA.certificate.row1.certificateNo").rawText()).contains("QWERTYU");
        assertThat(byPath(reading).get("partA.certificate.row1.lastUpdatedOn").rawText()).contains("01-Jun-2026");
        assertThat(reading.end()).isEqualTo(words.size() - 1);
    }

    @Test
    void read_whenAnUntitledTablesFirstHeadingIsNotPrinted_saysTheTableIsAbsent() {
        // When
        TableReading.Reading reading = TableReading.read(List.of(word(0, "Summary", 58, 110)), 0, BOXES, Optional.of(SUMMARY),
                Collections.newSetFromMap(new IdentityHashMap<>()));

        // Then
        assertThat(reading.found()).isFalse();
        assertThat(reading.notes()).containsExactly("Certificate No. is not in this certificate");
    }

    @Test
    void read_whenNoTableFollowsAOneRowTable_isIncompleteRatherThanReadingOn() {
        // When — C1a
        TableReading.Reading reading = TableReading.read(boxes(), 0, BOXES, Optional.empty(),
                Collections.newSetFromMap(new IdentityHashMap<>()));

        // Then
        assertThat(reading.complete()).isFalse();
        assertThat(reading.notes()).containsExactly("Not found: where partA.certificate's row ends (no table follows)");
        assertThat(reading.fields()).isEmpty();
    }

    @Test
    void read_whenACellPrintsCaptions_readsEachCaptionsValueAndLeavesWordsBeforeTheFirstUnplaced() {
        // Given — "CIT (TDS)" 58 – 110 with captions; "Room" printed before the first caption
        PrintedTable cit = new PrintedTable("partA.period", List.of(),
                List.of(new PrintedColumn("CIT (TDS)", "citTds", "TEXT", Optional.empty(), List.of("Address", "City", "Pin Code"))),
                Optional.empty(), List.of(), 28);
        List<Word> words = List.of(word(0, "CIT", 58, 75), word(0, "(TDS)", 79, 110), word(1, "Room", 58, 80),
                word(2, "Address", 58, 90), word(2, "BMTC", 94, 120), word(3, "City", 58, 75), word(3, "BENGALURU", 79, 130),
                word(4, "Pin", 58, 70), word(4, "Code", 74, 92), word(4, "560095", 96, 126), word(5, "Summary", 58, 110));

        // When
        TableReading.Reading reading = TableReading.read(words, 0, cit, Optional.of(SUMMARY),
                Collections.newSetFromMap(new IdentityHashMap<>()));

        // Then
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields.get("partA.period.row1.citTds.address").rawText()).contains("BMTC");
        assertThat(fields.get("partA.period.row1.citTds.city").rawText()).contains("BENGALURU");
        assertThat(fields.get("partA.period.row1.citTds.pinCode").rawText()).contains("560095");
        assertThat(fields).doesNotContainKey("partA.period.row1.citTds");
        assertThat(reading.complete()).isFalse();
        assertThat(reading.notes()).containsExactly("Not placed: partA.period.row1.citTds (Room)");
    }
}
