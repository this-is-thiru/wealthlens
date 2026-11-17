package com.thiru.wealthlens.taxplanning.documents.reconcile;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxplanning.documents.assembly.Assembly;
import com.thiru.wealthlens.taxplanning.documents.assembly.AssemblyMode;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** One employer's confirmed Form No. 130 against the computation ({@code DR 05} M4.2; W-130a, D-756). */
class ReconciliationTest {

    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final SalaryCertificateLaw LAW = new SalaryCertificateLaw(NEW,
            Map.of(NEW, Money.of("75000"), new RegimeCode("FIRST_SCHEDULE_RATES"), Money.of("50000")), Rate.percent("4"));
    private static final Assembly ONE = new Assembly(AssemblyMode.PER_EMPLOYER, List.of());

    /** Row A by fact; rows 12 and 17 and Part B's total paired as FORM_130.json pairs them (W-130a). */
    private static final CertificateLayout LAYOUT = new CertificateLayout(new CertificateCode("FORM_130"), "T", List.of(),
            List.of(new CertificateLayout.PrintedLine("c.rowA", Optional.empty(), "A", "YES_NO", List.of(), false, 1,
                    Optional.of("REGIME_OPT_OUT"))),
            List.of(), List.of(), List.of(),
            List.of(new CertificateLayout.PrintedComparison("c.row12", Optional.of("round.totalincome"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                    new CertificateLayout.PrintedComparison("c.row17", Optional.empty(), Optional.of("BILL"), Optional.empty(), Optional.empty(), Optional.empty()),
                    new CertificateLayout.PrintedComparison("b.total.taxDeducted", Optional.empty(), Optional.of("BILL"), Optional.of("TAXSUM"), Optional.empty(), Optional.empty())),
            "test");

    private static DocumentConfirmation confirmed(boolean optsOut, String row12, String row17, String partB) {
        Map<String, ExtractedValue> accepted = new HashMap<>(Map.of("c.rowA", new ExtractedValue.FlagRead(optsOut),
                "c.row12", new ExtractedValue.MoneyRead(Money.of(row12)), "c.row17", new ExtractedValue.MoneyRead(Money.of(row17)),
                "b.total.taxDeducted", new ExtractedValue.MoneyRead(Money.of(partB))));
        return new DocumentConfirmation("attempt-d1", "user", LocalDateTime.of(2027, 6, 1, 10, 0), accepted, Set.of());
    }

    private static ComputedTax computed(String regime, String totalIncome, String bill, List<ComputedTax.Bucket> buckets) {
        List<ComputedTax.Step> trace = List.of(new ComputedTax.Step(1, "round.totalincome", "RATE-ROUND-TOTAL-INCOME", "ROUNDING", null,
                Optional.of(Money.of(totalIncome)), Optional.of(Money.of(totalIncome)), ""));
        return new ComputedTax("comp-1", "r", "2026-27", regime, Money.of(totalIncome), Money.of(bill), Money.ZERO, null, List.of(),
                List.of(), Money.of(bill), "fp", List.of(), trace, buckets, List.of(), Money.ZERO);
    }

    private static ReconciliationReport reconcile(DocumentConfirmation confirmation, ComputedTax computed) {
        return Reconciliation.reconcile(List.of(confirmation), ONE, LAYOUT, computed, LAW);
    }

    @Test
    void reconcile_whenPartBDeductedTheWholeBill_isMatchedAndSaysWhyPartBIsComparedWithTheBill() {
        // When — T8-4: Part B 2,14,500 = rows 13 + 15 + 16 − 14 = 2,14,500, the bill
        ReconciliationReport report = reconcile(confirmed(false, "2025000", "214500", "214500"),
                computed("NEW_TAX_REGIME", "2025000", "214500", List.of()));

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MATCHED);
        assertThat(report.computationId()).isEqualTo("comp-1");
        assertThat(report.documentIds()).containsExactly("attempt-d1");
        assertThat(report.lines()).extracting(ReconciliationLine::printed).containsExactly("c.row12", "c.row17", "b.total.taxDeducted");
        assertThat(report.lines().get(2).reasons()).contains(ReconciliationReason.TAXSUM);
        assertThat(report.lines().get(2).difference()).isEqualTo(Money.ZERO);
    }

    @Test
    void reconcile_whenAFigureIsTenRupeesOrLessOff_isMatchedByRounding() {
        // When — row 12 prints 20,25,007 against 20,25,000: ₹7
        ReconciliationReport report = reconcile(confirmed(false, "2025007", "214500", "214500"),
                computed("NEW_TAX_REGIME", "2025000", "214500", List.of()));

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MATCHED);
        assertThat(report.lines().get(0).reasons()).contains(ReconciliationReason.ROUNDING);
        assertThat(report.lines().get(0).difference()).isEqualTo(Money.of("7"));
    }

    @Test
    void reconcile_whenTheEmployerUsedAnotherRegime_isAMinorDifference() {
        // Given — row A "Yes" (opted out of the default, new regime) while the computation is under the new regime; row 12
        // 20,50,000 against 20,25,000, ₹25,000 apart
        ReconciliationReport report = reconcile(confirmed(true, "2050000", "214500", "214500"),
                computed("NEW_TAX_REGIME", "2025000", "214500", List.of()));

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MINOR_DIFFERENCE);
        assertThat(report.lines().get(0).reasons()).contains(ReconciliationReason.REGIME_DIFFERS);
    }

    @Test
    void reconcile_whenTheComputationCarriesCapitalGains_isAMinorDifference() {
        // Given — the bill 2,64,500 includes tax on a ₹2,00,000 gain no Form No. 130 can show; Part B and row 17 2,14,500
        ComputedTax withGains = computed("NEW_TAX_REGIME", "2225000", "264500",
                List.of(new ComputedTax.Bucket(null, Money.of("200000"), List.of(), "LONG", "row", 13, null)));

        // When
        ReconciliationReport report = reconcile(confirmed(false, "2025000", "214500", "214500"), withGains);

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MINOR_DIFFERENCE);
        assertThat(report.lines()).allSatisfy(line -> assertThat(line.reasons()).isNotEmpty());
        assertThat(report.lines().get(1).reasons()).contains(ReconciliationReason.CAPITAL_GAINS);
    }

    @Test
    void reconcile_whenNothingExplainsADifference_isMaterial() {
        // Given — Part B deducted 1,64,500 against a bill of 2,14,500: ₹50,000 short, same regime, no gains
        ReconciliationReport report = reconcile(confirmed(false, "2025000", "214500", "164500"),
                computed("NEW_TAX_REGIME", "2025000", "214500", List.of()));

        // Then — D-755 C2a: an unexplained difference is material
        assertThat(report.verdict()).isEqualTo(Verdict.MATERIAL_DIFFERENCE);
        assertThat(report.lines().get(2).difference()).isEqualTo(Money.of("-50000"));
        assertThat(report.lines().get(2).reasons()).isEmpty();
    }

    @Test
    void reconcile_whenTheYearIsMixed_cannotCompare() {
        // When — W-129's MIXED: some employers issued Part C and some did not
        ReconciliationReport report = Reconciliation.reconcile(List.of(confirmed(false, "2025000", "214500", "214500")),
                new Assembly(AssemblyMode.MIXED, List.of()), LAYOUT, computed("NEW_TAX_REGIME", "2025000", "214500", List.of()), LAW);

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.CANNOT_COMPARE);
        assertThat(report.lines()).isEmpty();
    }

    /** Row 11 (Deductible Amount) first, as the form prints it, then row 12, row 17 and Part B (W-130b). */
    private static final CertificateLayout CLAIMS = new CertificateLayout(new CertificateCode("FORM_130"), "T", List.of(), LAYOUT.lines(),
            List.of(), List.of(), List.of(),
            List.of(new CertificateLayout.PrintedComparison("c.row11.deductibleAmount", Optional.empty(), Optional.of("CHAPTER_VIII"),
                    Optional.empty(), Optional.of("DECLARED_NOT_PROVEN"), Optional.of("ENTITLED_NOT_DECLARED"))).stream()
                    .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), claim -> {
                        claim.addAll(LAYOUT.comparisons());
                        return claim;
                    })),
            "test");

    private static ComputedTax withClaims(String totalIncome, String bill, String chapterEight) {
        ComputedTax plain = computed("NEW_TAX_REGIME", totalIncome, bill, List.of());
        return new ComputedTax(plain.computationId(), plain.receipt(), plain.taxYear(), plain.regime(), plain.totalIncome(), plain.bill(),
                plain.credits(), null, List.of(), List.of(), plain.finalAmount(), "fp", List.of(), plain.trace(), List.of(), List.of(),
                Money.of(chapterEight));
    }

    private static DocumentConfirmation withRow11(DocumentConfirmation confirmation, String row11) {
        Map<String, ExtractedValue> accepted = new HashMap<>(confirmation.acceptedFields());
        accepted.put("c.row11.deductibleAmount", new ExtractedValue.MoneyRead(Money.of(row11)));
        return new DocumentConfirmation(confirmation.attemptId(), confirmation.confirmedBy(), confirmation.confirmedAt(), accepted, Set.of());
    }

    @Test
    void reconcile_whenAClaimWasDeclaredButNotProven_isMinorAndEveryLineItMovesSaysSo() {
        // Given — T8-2: row 11 1,50,000 declared, 60,000 proven, ₹90,000 apart; so the employer's total income 10,00,000 is
        // ₹90,000 below the computation's 10,90,000, and its tax 82,000 and Part B 82,000 fall short of the bill 1,00,000
        DocumentConfirmation certificate = withRow11(confirmed(false, "1000000", "82000", "82000"), "150000");

        // When
        ReconciliationReport report = Reconciliation.reconcile(List.of(certificate), ONE, CLAIMS, withClaims("1090000", "100000", "60000"), LAW);

        // Then — D-755 C2a: an expected reason, carried to the lines it moves (W-130b C3a)
        assertThat(report.verdict()).isEqualTo(Verdict.MINOR_DIFFERENCE);
        assertThat(report.lines().get(0).difference()).isEqualTo(Money.of("90000"));
        assertThat(report.lines()).allSatisfy(line -> assertThat(line.reasons()).contains(ReconciliationReason.DECLARED_NOT_PROVEN));
    }

    @Test
    void reconcile_whenADeductionWasNeverDeclared_isMinorWithARefundDue() {
        // Given — T8-3: ₹25,000 of health insurance never told to the employer; row 11 prints 0 against 25,000, and the
        // employer's total income 10,00,000 is ₹25,000 above the computation's 9,75,000
        DocumentConfirmation certificate = withRow11(confirmed(false, "1000000", "100000", "100000"), "0");

        // When
        ReconciliationReport report = Reconciliation.reconcile(List.of(certificate), ONE, CLAIMS, withClaims("975000", "92500", "25000"), LAW);

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MINOR_DIFFERENCE);
        assertThat(report.lines().get(0).reasons()).contains(ReconciliationReason.ENTITLED_NOT_DECLARED);
        assertThat(report.lines().get(1).reasons()).contains(ReconciliationReason.ENTITLED_NOT_DECLARED);
    }

    @Test
    void reconcile_whenTheClaimsAgreeButALaterLineDiffers_leavesItUnexplained() {
        // Given — row 11 equal (60,000 both), but row 12 10,00,000 against 10,50,000 for no claim reason
        DocumentConfirmation certificate = withRow11(confirmed(false, "1000000", "100000", "100000"), "60000");

        // When
        ReconciliationReport report = Reconciliation.reconcile(List.of(certificate), ONE, CLAIMS, withClaims("1050000", "100000", "60000"), LAW);

        // Then — nothing above it explains row 12
        assertThat(report.verdict()).isEqualTo(Verdict.MATERIAL_DIFFERENCE);
        assertThat(report.lines().get(1).reasons()).isEmpty();
    }

    /** Two or more employers: Part A's period, Annexure-I's row A and 1(e), and Part B as a table whose Total is summed. */
    private static final CertificateLayout YEAR = new CertificateLayout(new CertificateCode("FORM_130"), "T", List.of(),
            List.of(new CertificateLayout.PrintedLine("a.period", Optional.empty(), "Period", "PERIOD", List.of(), false, 1,
                            Optional.of("EMPLOYMENT_PERIOD")),
                    new CertificateLayout.PrintedLine("c.rowA", Optional.empty(), "A", "YES_NO", List.of(), false, 1, Optional.of("REGIME_OPT_OUT")),
                    new CertificateLayout.PrintedLine("c.row1e", Optional.empty(), "1(e)", "AMOUNT", List.of(), false, 1,
                            Optional.of("SALARY_FROM_OTHER_EMPLOYERS"))),
            List.of(),
            List.of(new CertificateLayout.PrintedTable("b", List.of("Summary"),
                    List.of(new CertificateLayout.PrintedColumn("Tax deducted", "taxDeducted", "AMOUNT", Optional.empty(), List.of())),
                    Optional.of("Total"), List.of(List.of("taxDeducted")), 1)),
            List.of(), LAYOUT.comparisons(), "test");

    /** One employer's certificate: its period and Part B; Part C's rows where {@code partC} holds row 12, row 17 and 1(e). */
    private static DocumentConfirmation employer(String attempt, String from, String to, String partB, Optional<String[]> partC) {
        Map<String, ExtractedValue> accepted = new HashMap<>(Map.of(
                "a.period", new ExtractedValue.PeriodRead(java.time.LocalDate.parse(from), java.time.LocalDate.parse(to)),
                "b.total.taxDeducted", new ExtractedValue.MoneyRead(Money.of(partB))));
        partC.ifPresent(rows -> {
            accepted.put("c.rowA", new ExtractedValue.FlagRead(false));
            accepted.put("c.row12", new ExtractedValue.MoneyRead(Money.of(rows[0])));
            accepted.put("c.row17", new ExtractedValue.MoneyRead(Money.of(rows[1])));
            accepted.put("c.row1e", new ExtractedValue.MoneyRead(Money.of(rows[2])));
        });
        return new DocumentConfirmation(attempt, "user", LocalDateTime.of(2027, 6, 1, 10, 0), accepted, Set.of());
    }

    private static Optional<String[]> partC(String row12, String row17, String row1e) {
        return Optional.of(new String[] {row12, row17, row1e});
    }

    @Test
    void reconcile_whenTwoEmployersEachDeductedForTheirOwnSalaryOnly_isMaterialForBothEmployersErrors() {
        // Given — T8-1: A (9,00,000) and B (12,00,000) each give the standard deduction and a full rebate, so each deducts 0;
        // the year's liability is 2,14,500. B's 1(e) is nil though A paid first (W-129 flags the deduction twice)
        List<DocumentConfirmation> year = List.of(
                employer("a", "2026-04-01", "2026-09-30", "0", partC("825000", "0", "0")),
                employer("b", "2026-10-01", "2027-03-31", "0", partC("1125000", "0", "0")));
        Assembly assembly = new Assembly(AssemblyMode.PER_EMPLOYER, List.of(new com.thiru.wealthlens.taxplanning.documents.assembly
                .AssemblyNotice(com.thiru.wealthlens.taxplanning.documents.assembly.AssemblyNotice.Level.ACTION_REQUIRED, "twice",
                        List.of("a", "b"), List.of("SAL-STDDED-001", "SAL-STDDED-002"))));

        // When
        ReconciliationReport report = Reconciliation.reconcile(year, assembly, YEAR, computed("NEW_TAX_REGIME", "2025000", "214500",
                List.of()), LAW);

        // Then — the year's 0 + 0 = 0 deducted against the bill 2,14,500: ₹2,14,500 short (DR 08 T8-1)
        assertThat(report.verdict()).isEqualTo(Verdict.MATERIAL_DIFFERENCE);
        assertThat(report.lines()).singleElement().satisfies(line -> {
            assertThat(line.printed()).isEqualTo("b.total.taxDeducted");
            assertThat(line.difference()).isEqualTo(Money.of("-214500"));
            assertThat(line.reasons()).containsExactly(ReconciliationReason.STANDARD_DEDUCTION_TWICE,
                    ReconciliationReason.PREVIOUS_SALARY_UNDECLARED);
        });
    }

    @Test
    void reconcile_whenTheLastEmployerConsolidatesAndTheirPartBsCoverTheBill_isMatched() {
        // Given — A issues Part A/B only and deducted 1,00,000; B's Part C covers the year (1(e) 9,00,000) and B deducted
        // 1,14,500: 1,00,000 + 1,14,500 = 2,14,500, the bill
        List<DocumentConfirmation> year = List.of(employer("a", "2026-04-01", "2026-09-30", "100000", Optional.empty()),
                employer("b", "2026-10-01", "2027-03-31", "114500", partC("2025000", "214500", "900000")));

        // When
        ReconciliationReport report = Reconciliation.reconcile(year, new Assembly(AssemblyMode.LAST_EMPLOYER_CONSOLIDATED, List.of()), YEAR,
                computed("NEW_TAX_REGIME", "2025000", "214500", List.of()), LAW);

        // Then — B's rows as one employer's, Part B summed over both
        assertThat(report.verdict()).isEqualTo(Verdict.MATCHED);
        assertThat(report.lines()).extracting(ReconciliationLine::printed).containsExactly("c.row12", "c.row17", "b.total.taxDeducted");
        assertThat(report.lines().get(2).documentValue()).isEqualTo(Money.of("214500"));
    }

    @Test
    void reconcile_whenTheConsolidatingEmployerLeftOutTheEarlierSalary_isMaterial() {
        // Given — B's Part C omits A's 9,00,000 (1(e) nil, W-129's notice): its row 12 11,25,000 against 20,25,000, and Part B
        // 0 + 0 against the bill 2,14,500
        List<DocumentConfirmation> year = List.of(employer("a", "2026-04-01", "2026-09-30", "0", Optional.empty()),
                employer("b", "2026-10-01", "2027-03-31", "0", partC("1125000", "0", "0")));
        Assembly assembly = new Assembly(AssemblyMode.LAST_EMPLOYER_CONSOLIDATED, List.of(new com.thiru.wealthlens.taxplanning.documents
                .assembly.AssemblyNotice(com.thiru.wealthlens.taxplanning.documents.assembly.AssemblyNotice.Level.ACTION_REQUIRED, "nil",
                        List.of("b"), List.of("TDS-SAL-12B-001"))));

        // When
        ReconciliationReport report = Reconciliation.reconcile(year, assembly, YEAR, computed("NEW_TAX_REGIME", "2025000", "214500",
                List.of()), LAW);

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MATERIAL_DIFFERENCE);
        assertThat(report.lines().get(2).reasons()).containsExactly(ReconciliationReason.PREVIOUS_SALARY_UNDECLARED);
    }
}
