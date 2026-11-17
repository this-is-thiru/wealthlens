package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedColumn;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedSum;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedTable;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.SumCell;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** A certificate checked against its own arithmetic ({@code DR 06} G5.2; W-128a, D-750): printed sums and tables' totals. */
class ContradictionsTest {

    /** Row 12 = 9 − 11, as Form No. 130 prints it. */
    private static final PrintedSum ROW_12 = new PrintedSum(new SumCell("c.row12", "c.row12", 1),
            List.of(new SumCell("c.row9", "c.row9", 1), new SumCell("c.row11", "c.row11", -1)), 2278);
    /** A summary table: quarter, the merged "Amount paid · Rate" Total cell, and tax deducted. */
    private static final PrintedTable SUMMARY = new PrintedTable("b.summary", List.of("Summary"),
            List.of(new PrintedColumn("Quarter", "quarter", "TEXT", Optional.empty(), List.of()),
                    new PrintedColumn("Amount paid", "amountPaid", "AMOUNT", Optional.empty(), List.of()),
                    new PrintedColumn("Rate", "rate", "TEXT", Optional.empty(), List.of()),
                    new PrintedColumn("Tax deducted", "taxDeducted", "AMOUNT", Optional.empty(), List.of())),
            Optional.of("Total"), List.of(List.of("quarter"), List.of("amountPaid", "rate"), List.of("taxDeducted")), 2274);

    private static ExtractedField amount(String path, String rupees) {
        return new ExtractedField(path, Optional.of(rupees), Optional.of(new ExtractedValue.MoneyRead(Money.of(rupees))), 0.90,
                Optional.empty(), Optional.empty());
    }

    private static ExtractedField unread(String path, String text) {
        return new ExtractedField(path, Optional.of(text), Optional.empty(), 0.95, Optional.empty(), Optional.empty());
    }

    private static Map<String, ExtractedField> byPath(Contradictions.Checked checked) {
        return checked.fields().stream().collect(Collectors.toMap(ExtractedField::fieldPath, Function.identity()));
    }

    @Test
    void check_whenAPrintedSumAgrees_saysNothingAndKeepsEveryConfidence() {
        // Given — 9 = 5,00,000, 11 = 1,50,000, 12 = 3,50,000: 5,00,000 − 1,50,000 = 3,50,000
        List<ExtractedField> fields = List.of(amount("c.row9", "500000"), amount("c.row11", "150000"), amount("c.row12", "350000"));

        // When
        Contradictions.Checked checked = Contradictions.check(fields, List.of(ROW_12), List.of());

        // Then
        assertThat(checked.notes()).isEmpty();
        assertThat(checked.fields()).allSatisfy(field -> assertThat(field.confidence()).isEqualTo(0.90));
    }

    @Test
    void check_whenAPrintedSumDisagrees_namesBothFiguresAndLowersEveryCellOfIt() {
        // Given — 12 printed 3,60,000 where 5,00,000 − 1,50,000 = 3,50,000
        List<ExtractedField> fields = List.of(amount("c.row9", "500000"), amount("c.row11", "150000"), amount("c.row12", "360000"),
                amount("c.row13", "1000"));

        // When
        Contradictions.Checked checked = Contradictions.check(fields, List.of(ROW_12), List.of());

        // Then — D-750 C2a: 0.50 on the sum's three cells, row 13 untouched
        assertThat(checked.notes()).containsExactly(
                "Contradiction: c.row12 printed ₹3,60,000, the sum printed for it gives ₹3,50,000");
        Map<String, ExtractedField> fields2 = byPath(checked);
        assertThat(List.of("c.row9", "c.row11", "c.row12")).allSatisfy(path -> assertThat(fields2.get(path).confidence()).isEqualTo(0.50));
        assertThat(fields2.get("c.row13").confidence()).isEqualTo(0.90);
    }

    @Test
    void check_whenACellOfTheSumIsNotRead_leavesTheSumUnchecked() {
        // Given — row 11 printed as text L2 could not read
        List<ExtractedField> fields = List.of(amount("c.row9", "500000"), unread("c.row11", "1,50,0OO"), amount("c.row12", "360000"));

        // When / Then
        assertThat(Contradictions.check(fields, List.of(ROW_12), List.of()).notes()).isEmpty();
    }

    @Test
    void check_whenATablesTotalIsNotTheSumOfItsRows_namesBothFiguresAndLowersTheColumn() {
        // Given — 10,000 + 5,000 = 15,000 under a Total of 16,000
        List<ExtractedField> fields = List.of(amount("b.summary.row1.taxDeducted", "10000"), amount("b.summary.row2.taxDeducted", "5000"),
                amount("b.summary.total.taxDeducted", "16000"));

        // When
        Contradictions.Checked checked = Contradictions.check(fields, List.of(), List.of(SUMMARY));

        // Then
        assertThat(checked.notes()).containsExactly(
                "Contradiction: b.summary.total.taxDeducted printed ₹16,000, its rows add to ₹15,000");
        assertThat(checked.fields()).allSatisfy(field -> assertThat(field.confidence()).isEqualTo(0.50));
    }

    @Test
    void check_whenARowsCellIsBlank_countsItAsNothing() {
        // Given — C1a: row 2 prints no tax deducted; 10,000 + nothing = 10,000 agrees, and is 2,000 short of 12,000
        List<ExtractedField> agrees = List.of(amount("b.summary.row1.taxDeducted", "10000"), amount("b.summary.total.taxDeducted", "10000"));
        List<ExtractedField> short2 = List.of(amount("b.summary.row1.taxDeducted", "10000"), amount("b.summary.total.taxDeducted", "12000"));

        // When / Then
        assertThat(Contradictions.check(agrees, List.of(), List.of(SUMMARY)).notes()).isEmpty();
        assertThat(Contradictions.check(short2, List.of(), List.of(SUMMARY)).notes()).containsExactly(
                "Contradiction: b.summary.total.taxDeducted printed ₹12,000, its rows add to ₹10,000");
    }

    @Test
    void check_whenARowsCellIsUnreadText_leavesThatColumnUnchecked() {
        // Given — C1a: a cell L2 could not read may hold the missing 2,000
        List<ExtractedField> fields = List.of(amount("b.summary.row1.taxDeducted", "10000"), unread("b.summary.row2.taxDeducted", "2,OOO"),
                amount("b.summary.total.taxDeducted", "12000"));

        // When / Then
        assertThat(Contradictions.check(fields, List.of(), List.of(SUMMARY)).notes()).isEmpty();
    }

    @Test
    void check_whenTheMergedTotalCellStartsWithAnAmount_checksThatAmount() {
        // Given — C2a: "Amount paid · Rate" checked on amount paid: 3,00,000 + 2,00,000 = 5,00,000, not 4,00,000
        List<ExtractedField> fields = List.of(amount("b.summary.row1.amountPaid", "300000"), amount("b.summary.row2.amountPaid", "200000"),
                amount("b.summary.total.amountPaid", "400000"));

        // When / Then
        assertThat(Contradictions.check(fields, List.of(), List.of(SUMMARY)).notes()).containsExactly(
                "Contradiction: b.summary.total.amountPaid printed ₹4,00,000, its rows add to ₹5,00,000");
    }

    @Test
    void check_whenATotalCellStartsWithText_doesNotCheckIt() {
        // Given — a Total cell under the TEXT column "Quarter", printing a figure
        List<ExtractedField> fields = List.of(amount("b.summary.row1.quarter", "1"), amount("b.summary.total.quarter", "9"));

        // When / Then
        assertThat(Contradictions.check(fields, List.of(), List.of(SUMMARY)).notes()).isEmpty();
    }
}
