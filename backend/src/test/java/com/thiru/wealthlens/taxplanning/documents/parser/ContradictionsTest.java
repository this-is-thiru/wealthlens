package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedAgreement;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedColumn;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedSum;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedTable;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.SumCell;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
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

    private static final TaxYearId YEAR = TaxYearId.of(2026);
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode OTHER = new RegimeCode("FIRST_SCHEDULE_RATES");
    /** TY 2026-27's law as the pack states it (`SalaryCertificateLawTest`): ₹75,000 new, ₹50,000 otherwise, cess 4%. */
    private static final SalaryCertificateLaw LAW = new SalaryCertificateLaw(NEW, Map.of(NEW, Money.of("75000"), OTHER, Money.of("50000")),
            Rate.percent("4"));

    private static CertificateLayout layout(List<PrintedSum> sums, List<PrintedTable> tables, List<PrintedAgreement> agreements,
            List<CertificateLayout.PrintedLine> lines) {
        return new CertificateLayout(new CertificateCode("SYN_1"), "T", List.of(), lines, sums, tables, agreements, "test");
    }

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
        Contradictions.Checked checked = Contradictions.check(fields, layout(List.of(ROW_12), List.of(), List.of(), List.of()), LAW, YEAR);

        // Then
        assertThat(checked.contradictions()).isEmpty();
        assertThat(checked.fields()).allSatisfy(field -> assertThat(field.confidence()).isEqualTo(0.90));
    }

    @Test
    void check_whenAPrintedSumDisagrees_namesBothFiguresAndLowersEveryCellOfIt() {
        // Given — 12 printed 3,60,000 where 5,00,000 − 1,50,000 = 3,50,000
        List<ExtractedField> fields = List.of(amount("c.row9", "500000"), amount("c.row11", "150000"), amount("c.row12", "360000"),
                amount("c.row13", "1000"));

        // When
        Contradictions.Checked checked = Contradictions.check(fields, layout(List.of(ROW_12), List.of(), List.of(), List.of()), LAW, YEAR);

        // Then — D-750 C2a: 0.50 on the sum's three cells, row 13 untouched
        assertThat(checked.contradictions()).containsExactly(
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
        assertThat(Contradictions.check(fields, layout(List.of(ROW_12), List.of(), List.of(), List.of()), LAW, YEAR).contradictions()).isEmpty();
    }

    @Test
    void check_whenATablesTotalIsNotTheSumOfItsRows_namesBothFiguresAndLowersTheColumn() {
        // Given — 10,000 + 5,000 = 15,000 under a Total of 16,000
        List<ExtractedField> fields = List.of(amount("b.summary.row1.taxDeducted", "10000"), amount("b.summary.row2.taxDeducted", "5000"),
                amount("b.summary.total.taxDeducted", "16000"));

        // When
        Contradictions.Checked checked = Contradictions.check(fields, layout(List.of(), List.of(SUMMARY), List.of(), List.of()), LAW, YEAR);

        // Then
        assertThat(checked.contradictions()).containsExactly(
                "Contradiction: b.summary.total.taxDeducted printed ₹16,000, its rows add to ₹15,000");
        assertThat(checked.fields()).allSatisfy(field -> assertThat(field.confidence()).isEqualTo(0.50));
    }

    @Test
    void check_whenARowsCellIsBlank_countsItAsNothing() {
        // Given — C1a: row 2 prints no tax deducted; 10,000 + nothing = 10,000 agrees, and is 2,000 short of 12,000
        List<ExtractedField> agrees = List.of(amount("b.summary.row1.taxDeducted", "10000"), amount("b.summary.total.taxDeducted", "10000"));
        List<ExtractedField> short2 = List.of(amount("b.summary.row1.taxDeducted", "10000"), amount("b.summary.total.taxDeducted", "12000"));

        // When / Then
        assertThat(Contradictions.check(agrees, layout(List.of(), List.of(SUMMARY), List.of(), List.of()), LAW, YEAR).contradictions()).isEmpty();
        assertThat(Contradictions.check(short2, layout(List.of(), List.of(SUMMARY), List.of(), List.of()), LAW, YEAR).contradictions()).containsExactly(
                "Contradiction: b.summary.total.taxDeducted printed ₹12,000, its rows add to ₹10,000");
    }

    @Test
    void check_whenARowsCellIsUnreadText_leavesThatColumnUnchecked() {
        // Given — C1a: a cell L2 could not read may hold the missing 2,000
        List<ExtractedField> fields = List.of(amount("b.summary.row1.taxDeducted", "10000"), unread("b.summary.row2.taxDeducted", "2,OOO"),
                amount("b.summary.total.taxDeducted", "12000"));

        // When / Then
        assertThat(Contradictions.check(fields, layout(List.of(), List.of(SUMMARY), List.of(), List.of()), LAW, YEAR).contradictions()).isEmpty();
    }

    @Test
    void check_whenTheMergedTotalCellStartsWithAnAmount_checksThatAmount() {
        // Given — C2a: "Amount paid · Rate" checked on amount paid: 3,00,000 + 2,00,000 = 5,00,000, not 4,00,000
        List<ExtractedField> fields = List.of(amount("b.summary.row1.amountPaid", "300000"), amount("b.summary.row2.amountPaid", "200000"),
                amount("b.summary.total.amountPaid", "400000"));

        // When / Then
        assertThat(Contradictions.check(fields, layout(List.of(), List.of(SUMMARY), List.of(), List.of()), LAW, YEAR).contradictions()).containsExactly(
                "Contradiction: b.summary.total.amountPaid printed ₹4,00,000, its rows add to ₹5,00,000");
    }

    @Test
    void check_whenATotalCellStartsWithText_doesNotCheckIt() {
        // Given — a Total cell under the TEXT column "Quarter", printing a figure
        List<ExtractedField> fields = List.of(amount("b.summary.row1.quarter", "1"), amount("b.summary.total.quarter", "9"));

        // When / Then
        assertThat(Contradictions.check(fields, layout(List.of(), List.of(SUMMARY), List.of(), List.of()), LAW, YEAR).contradictions()).isEmpty();
    }

    private static ExtractedField period(String from, String to) {
        return new ExtractedField("a.period", Optional.of(from + " to " + to), Optional.of(new ExtractedValue.PeriodRead(
                java.time.LocalDate.parse(from), java.time.LocalDate.parse(to))), 0.90, Optional.empty(), Optional.empty());
    }

    @Test
    void check_whenThePeriodIsInsideTheTaxYear_saysNothing() {
        // When / Then — a part year, 15-07-2026 to 31-03-2027, inside TY 2026-27
        assertThat(Contradictions.check(List.of(period("2026-07-15", "2027-03-31")), layout(List.of(), List.of(), List.of(), List.of()), LAW, YEAR)
                .contradictions()).isEmpty();
    }

    @Test
    void check_whenThePeriodIsOutsideTheTaxYearOrReversed_isAContradiction() {
        // When
        Contradictions.Checked outside = Contradictions.check(List.of(period("2025-04-01", "2026-03-31")), layout(List.of(), List.of(), List.of(), List.of()), LAW, YEAR);
        Contradictions.Checked reversed = Contradictions.check(List.of(period("2027-03-31", "2026-04-01")), layout(List.of(), List.of(), List.of(), List.of()), LAW, YEAR);

        // Then — D-750 C2a
        assertThat(outside.contradictions()).containsExactly(
                "Contradiction: a.period printed 01/04/2025 to 31/03/2026, outside tax year 2026-27");
        assertThat(outside.fields().get(0).confidence()).isEqualTo(0.50);
        assertThat(Contradictions.check(List.of(period("2026-04-01", "2027-04-30")), layout(List.of(), List.of(), List.of(), List.of()), LAW, YEAR).contradictions())
                .containsExactly("Contradiction: a.period printed 01/04/2026 to 30/04/2027, outside tax year 2026-27");
        assertThat(reversed.contradictions()).containsExactly(
                "Contradiction: a.period printed 31/03/2027 to 01/04/2026, its first day after its last");
    }

    private static final PrintedAgreement PART_B = new PrintedAgreement("b.total.tax", "c.row21", Money.of("10"), "DR 06 G5.2");

    @Test
    void check_whenTwoAgreeingAmountsAreWithinTheTolerance_saysNothing() {
        // When / Then — 1,20,000 against 1,20,010: ₹10 apart, at the tolerance
        assertThat(Contradictions.check(List.of(amount("b.total.tax", "120000"), amount("c.row21", "120010")), layout(List.of(), List.of(), List.of(PART_B), List.of()), LAW, YEAR).differences()).isEmpty();
    }

    @Test
    void check_whenTwoAgreeingAmountsDifferBeyondTheTolerance_showsTheDifferenceAndTrustsBoth() {
        // Given — 1,20,000 against 1,20,010.01: ₹10.01 apart; 1,00,000 against 1,20,000: ₹20,000
        Contradictions.Checked justOver = Contradictions.check(List.of(amount("b.total.tax", "120000"), amount("c.row21", "120010.01")),
                layout(List.of(), List.of(), List.of(PART_B), List.of()), LAW, YEAR);
        Contradictions.Checked short3 = Contradictions.check(List.of(amount("b.total.tax", "100000"), amount("c.row21", "120000")),
                layout(List.of(), List.of(), List.of(PART_B), List.of()), LAW, YEAR);

        // Then — D-750 C4a: a difference shown, not a contradiction; confidences kept (W-128b C4a)
        assertThat(justOver.differences()).hasSize(1);
        assertThat(short3.differences()).containsExactly(
                "Differs: b.total.tax ₹1,00,000 and c.row21 ₹1,20,000 are ₹20,000 apart (DR 06 G5.2)");
        assertThat(short3.contradictions()).isEmpty();
        assertThat(short3.fields()).allSatisfy(field -> assertThat(field.confidence()).isEqualTo(0.90));
    }

    @Test
    void check_whenOneOfTwoAgreeingAmountsIsNotRead_saysNothing() {
        // When / Then
        assertThat(Contradictions.check(List.of(amount("b.total.tax", "100000"), unread("c.row21", "1,2O,000")), layout(List.of(), List.of(), List.of(PART_B), List.of()), LAW, YEAR).differences()).isEmpty();
    }

    private static CertificateLayout.PrintedLine line(String path, String kind, String fact) {
        return new CertificateLayout.PrintedLine(path, Optional.empty(), path, kind, List.of(), false, 1, Optional.of(fact));
    }

    /** One annexure's lines that state a fact (W-128c): row A, 4(b), 13 – 16, as Form No. 130's Annexure-I prints them. */
    private static final List<CertificateLayout.PrintedLine> FACTS = List.of(line("c.rowA", "YES_NO", "REGIME_OPT_OUT"),
            line("c.row4b", "AMOUNT", "STANDARD_DEDUCTION"), line("c.row13", "AMOUNT", "TAX_ON_TOTAL_INCOME"),
            line("c.row14", "AMOUNT", "REBATE"), line("c.row15", "AMOUNT", "SURCHARGE"), line("c.row16", "AMOUNT", "CESS"));

    private static ExtractedField flag(String path, boolean value) {
        return new ExtractedField(path, Optional.of(value ? "Yes" : "No"), Optional.of(new ExtractedValue.FlagRead(value)), 0.90,
                Optional.empty(), Optional.empty());
    }

    private static Contradictions.Checked againstTheLaw(ExtractedField... fields) {
        return Contradictions.check(List.of(fields), layout(List.of(), List.of(), List.of(), FACTS), LAW, YEAR);
    }

    @Test
    void check_whenTheStandardDeductionFitsRowAsRegime_saysNothing() {
        // When / Then — No keeps the new regime: ₹75,000 at its ceiling, or ₹40,000 for a salary below it ("whichever is less")
        assertThat(againstTheLaw(flag("c.rowA", false), amount("c.row4b", "75000")).contradictions()).isEmpty();
        assertThat(againstTheLaw(flag("c.rowA", false), amount("c.row4b", "40000")).contradictions()).isEmpty();
        assertThat(againstTheLaw(flag("c.rowA", true), amount("c.row4b", "50000")).contradictions()).isEmpty();
    }

    @Test
    void check_whenTheStandardDeductionIsAnotherRegimes_isAContradictionOnBothCells() {
        // When — No keeps the new regime, whose ceiling is ₹75,000, but 4(b) prints the other regime's ₹50,000
        Contradictions.Checked checked = againstTheLaw(flag("c.rowA", false), amount("c.row4b", "50000"));

        // Then — G5.2's "high-signal" check: one of the two was misread or the form is wrong
        assertThat(checked.contradictions()).containsExactly("Contradiction: c.row4b printed ₹50,000, the standard deduction of"
                + " FIRST_SCHEDULE_RATES, but c.rowA's No keeps NEW_TAX_REGIME (₹75,000)");
        assertThat(checked.fields()).allSatisfy(field -> assertThat(field.confidence()).isEqualTo(0.50));
    }

    @Test
    void check_whenTheStandardDeductionIsAboveRowAsRegimesCeiling_isAContradiction() {
        // When / Then — ₹80,000 over the new regime's ₹75,000; ₹75,000 over the ₹50,000 a Yes leaves
        assertThat(againstTheLaw(flag("c.rowA", false), amount("c.row4b", "80000")).contradictions()).containsExactly(
                "Contradiction: c.row4b printed ₹80,000, above the ₹75,000 standard deduction of NEW_TAX_REGIME that c.rowA's No keeps");
        assertThat(againstTheLaw(flag("c.rowA", true), amount("c.row4b", "75000")).contradictions()).containsExactly(
                "Contradiction: c.row4b printed ₹75,000, above the ₹50,000 standard deduction of FIRST_SCHEDULE_RATES that c.rowA's Yes"
                        + " leaves");
    }

    @Test
    void check_whenRowAIsNotRead_leavesTheStandardDeductionUnchecked() {
        // When / Then
        assertThat(againstTheLaw(unread("c.rowA", "N0"), amount("c.row4b", "80000")).contradictions()).isEmpty();
    }

    @Test
    void check_whenTheCessIsFourPerCentOfTaxAndSurchargeLessRebate_saysNothing() {
        // When / Then — 4% of (1,00,000 + 10,000 − 0) = 4,400; ₹10 either side is ₹4,410 (G5.2's ≈); surcharge not printed is nothing
        assertThat(againstTheLaw(amount("c.row13", "100000"), amount("c.row14", "0"), amount("c.row15", "10000"),
                amount("c.row16", "4410")).contradictions()).isEmpty();
        assertThat(againstTheLaw(amount("c.row13", "100000"), amount("c.row16", "4000")).contradictions()).isEmpty();
    }

    @Test
    void check_whenTheCessIsMoreThanTenRupeesOff_isAContradiction() {
        // When — 4% of (1,00,000 + 10,000 − 0) = 4,400, printed 4,411
        Contradictions.Checked checked = againstTheLaw(amount("c.row13", "100000"), amount("c.row14", "0"), amount("c.row15", "10000"),
                amount("c.row16", "4411"));

        // Then
        assertThat(checked.contradictions()).containsExactly(
                "Contradiction: c.row16 printed ₹4,411, 4% of c.row13 + c.row15 − c.row14 gives ₹4,400");
        assertThat(checked.fields()).allSatisfy(field -> assertThat(field.confidence()).isEqualTo(0.50));
    }

    @Test
    void check_whenATermOfTheCessIsNotRead_leavesTheCessUnchecked() {
        // When / Then
        assertThat(againstTheLaw(amount("c.row13", "100000"), unread("c.row15", "1O,000"), amount("c.row16", "9999")).contradictions())
                .isEmpty();
    }
}
