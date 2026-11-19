package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedSum;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.SumCell;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.FieldLocation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** L3: a cell a printed sum determines is filled, and only such a cell (W-127b, {@code DR 06} G5.1). */
class InferenceTest {

    private static final String R = "partC.annexureI.row";
    private static final FieldLocation PLACE = new FieldLocation(1, 10, 10, 100, 10);
    /** Row 3 = 1(d) - 2(f), as p.2276 prints it. */
    private static final PrintedSum ROW_3 = new PrintedSum(cell("3", 1), List.of(cell("1d", 1), cell("2f", -1)), 2276);
    /** Row 6 = 3 + 1(e) - 5, p.2277. */
    private static final PrintedSum ROW_6 = new PrintedSum(cell("6", 1), List.of(cell("3", 1), cell("1e", 1), cell("5", -1)), 2277);

    private static SumCell cell(String row, int sign) {
        return new SumCell(R + row, R + row, sign);
    }

    private static ExtractedField read(String row, String rupees) {
        return new ExtractedField(R + row, Optional.of(rupees), Optional.of(new ExtractedValue.MoneyRead(Money.of(rupees))), 0.90,
                Optional.of(PLACE), Optional.empty());
    }

    private static ExtractedField blank(String row) {
        return new ExtractedField(R + row, Optional.empty(), Optional.empty(), 0.95, Optional.of(PLACE), Optional.empty());
    }

    private static Map<String, ExtractedField> byPath(Inference.Filled filled) {
        return filled.fields().stream().collect(Collectors.toMap(ExtractedField::fieldPath, Function.identity()));
    }

    private static Optional<ExtractedValue> valueOf(Inference.Filled filled, String row) {
        return byPath(filled).get(R + row).interpretedValue();
    }

    @Test
    void fill_whenOnlyTheTargetIsBlank_isTheSumOfItsTerms() {
        // When — 5,00,000 - 50,000 = 4,50,000
        Inference.Filled filled = Inference.fill(List.of(read("1d", "500000"), read("2f", "50000"), blank("3")), List.of(ROW_3));

        // Then
        ExtractedField row3 = byPath(filled).get(R + "3");
        assertThat(row3.interpretedValue()).contains(new ExtractedValue.MoneyRead(Money.of("450000")));
        assertThat(row3.confidence()).isEqualTo(0.80);
        assertThat(row3.rawText()).isEmpty();
        assertThat(row3.location()).contains(PLACE);
        assertThat(filled.notes()).containsExactly("Inferred partC.annexureI.row3 from the sum printed for partC.annexureI.row3");
    }

    @Test
    void fill_whenATermTakenAwayIsBlank_solvesForIt() {
        // When — 4,50,000 = 5,00,000 - 2(f), so 2(f) = 50,000
        Inference.Filled filled = Inference.fill(List.of(read("1d", "500000"), blank("2f"), read("3", "450000")), List.of(ROW_3));

        // Then
        assertThat(valueOf(filled, "2f")).contains(new ExtractedValue.MoneyRead(Money.of("50000")));
    }

    @Test
    void fill_whenATermAddedWasNotFound_solvesForItWithNoPlace() {
        // When — 4,50,000 = 1(d) - 50,000, so 1(d) = 5,00,000; the line was not found, so it has no place
        Inference.Filled filled = Inference.fill(List.of(read("2f", "50000"), read("3", "450000")), List.of(ROW_3));

        // Then
        ExtractedField row1d = byPath(filled).get(R + "1d");
        assertThat(row1d.interpretedValue()).contains(new ExtractedValue.MoneyRead(Money.of("500000")));
        assertThat(row1d.location()).isEmpty();
    }

    @Test
    void fill_whenOneFillMakesAnotherSumSolvable_fillsBoth() {
        // When — row 3 = 5,00,000 - 50,000 = 4,50,000; then row 6 = 4,50,000 + 0 - 50,000 = 4,00,000
        Inference.Filled filled = Inference.fill(List.of(read("1d", "500000"), read("2f", "50000"), blank("3"), read("1e", "0"),
                read("5", "50000"), blank("6")), List.of(ROW_6, ROW_3));

        // Then
        assertThat(valueOf(filled, "6")).contains(new ExtractedValue.MoneyRead(Money.of("400000")));
        assertThat(filled.notes()).hasSize(2);
    }

    @Test
    void fill_whenTwoCellsAreUnknown_fillsNeither() {
        // When
        Inference.Filled filled = Inference.fill(List.of(read("1d", "500000"), blank("2f"), blank("3")), List.of(ROW_3));

        // Then
        assertThat(valueOf(filled, "2f")).isEmpty();
        assertThat(valueOf(filled, "3")).isEmpty();
        assertThat(filled.notes()).isEmpty();
    }

    @Test
    void fill_whenACellPrintsTextNotRead_leavesTheSumAlone() {
        // Given — row 3 printed "Rs. 4,50,000", which L2 did not read
        ExtractedField unread = new ExtractedField(R + "3", Optional.of("Rs. 4,50,000"), Optional.empty(), 0.95, Optional.of(PLACE),
                Optional.empty());

        // When
        Inference.Filled filled = Inference.fill(List.of(read("1d", "500000"), read("2f", "50000"), unread), List.of(ROW_3));

        // Then — D-734 C2a: printed text is never replaced
        assertThat(byPath(filled).get(R + "3")).isEqualTo(unread);
        assertThat(filled.notes()).isEmpty();
    }

    @Test
    void fill_whenEveryCellIsReadAndTheyDisagree_changesNothing() {
        // When — 5,00,000 - 50,000 is not 4,00,000: W-128 reports that, L3 does not choose
        List<ExtractedField> fields = List.of(read("1d", "500000"), read("2f", "50000"), read("3", "400000"));
        Inference.Filled filled = Inference.fill(fields, List.of(ROW_3));

        // Then
        assertThat(filled.fields()).isEqualTo(fields);
        assertThat(filled.notes()).isEmpty();
    }

    @Test
    void fill_whenTheSumIsOfAColumn_fillsThatColumnsCell() {
        // Given — row 11's deductible cell = 10(a)'s + 10(b)'s: 1,50,000 + 25,000 = 1,75,000; row 11's line printed nothing
        SumCell total = new SumCell(R + "11", R + "11.deductibleAmount", 1);
        PrintedSum row11 = new PrintedSum(total, List.of(new SumCell(R + "10a", R + "10a.deductibleAmount", 1),
                new SumCell(R + "10b", R + "10b.deductibleAmount", 1)), 2278);
        List<ExtractedField> fields = List.of(
                new ExtractedField(R + "10a.deductibleAmount", Optional.of("1,50,000.00"),
                        Optional.of(new ExtractedValue.MoneyRead(Money.of("150000"))), 0.90, Optional.of(PLACE), Optional.empty()),
                new ExtractedField(R + "10b.deductibleAmount", Optional.of("25,000.00"),
                        Optional.of(new ExtractedValue.MoneyRead(Money.of("25000"))), 0.90, Optional.of(PLACE), Optional.empty()),
                blank("11"));

        // When
        Inference.Filled filled = Inference.fill(fields, List.of(row11));

        // Then
        assertThat(byPath(filled).get(R + "11.deductibleAmount").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("175000")));
    }
}
