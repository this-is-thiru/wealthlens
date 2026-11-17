package com.thiru.wealthlens.taxplanning.documents.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(report.lines().get(2).reason()).contains(ReconciliationReason.TAXSUM);
        assertThat(report.lines().get(2).difference()).isEqualTo(Money.ZERO);
    }

    @Test
    void reconcile_whenAFigureIsTenRupeesOrLessOff_isMatchedByRounding() {
        // When — row 12 prints 20,25,007 against 20,25,000: ₹7
        ReconciliationReport report = reconcile(confirmed(false, "2025007", "214500", "214500"),
                computed("NEW_TAX_REGIME", "2025000", "214500", List.of()));

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MATCHED);
        assertThat(report.lines().get(0).reason()).contains(ReconciliationReason.ROUNDING);
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
        assertThat(report.lines().get(0).reason()).contains(ReconciliationReason.REGIME_DIFFERS);
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
        assertThat(report.lines()).allSatisfy(line -> assertThat(line.reason()).isPresent());
        assertThat(report.lines().get(1).reason()).contains(ReconciliationReason.CAPITAL_GAINS);
    }

    @Test
    void reconcile_whenNothingExplainsADifference_isMaterial() {
        // Given — Part B deducted 1,64,500 against a bill of 2,14,500: ₹50,000 short, same regime, no gains
        ReconciliationReport report = reconcile(confirmed(false, "2025000", "214500", "164500"),
                computed("NEW_TAX_REGIME", "2025000", "214500", List.of()));

        // Then — D-755 C2a: an unexplained difference is material
        assertThat(report.verdict()).isEqualTo(Verdict.MATERIAL_DIFFERENCE);
        assertThat(report.lines().get(2).difference()).isEqualTo(Money.of("-50000"));
        assertThat(report.lines().get(2).reason()).isEmpty();
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

    @Test
    void reconcile_whenSeveralEmployersAreNotMixed_refusesUntilW130c() {
        // When / Then — several employers' reasons are W-130c's (D-755 C1a)
        DocumentConfirmation one = confirmed(false, "2025000", "214500", "214500");
        assertThatThrownBy(() -> Reconciliation.reconcile(List.of(one, one), ONE, LAYOUT,
                computed("NEW_TAX_REGIME", "2025000", "214500", List.of()), LAW)).isInstanceOf(IllegalArgumentException.class);
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
        assertThat(report.lines()).allSatisfy(line -> assertThat(line.reason()).contains(ReconciliationReason.DECLARED_NOT_PROVEN));
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
        assertThat(report.lines().get(0).reason()).contains(ReconciliationReason.ENTITLED_NOT_DECLARED);
        assertThat(report.lines().get(1).reason()).contains(ReconciliationReason.ENTITLED_NOT_DECLARED);
    }

    @Test
    void reconcile_whenTheClaimsAgreeButALaterLineDiffers_leavesItUnexplained() {
        // Given — row 11 equal (60,000 both), but row 12 10,00,000 against 10,50,000 for no claim reason
        DocumentConfirmation certificate = withRow11(confirmed(false, "1000000", "100000", "100000"), "60000");

        // When
        ReconciliationReport report = Reconciliation.reconcile(List.of(certificate), ONE, CLAIMS, withClaims("1050000", "100000", "60000"), LAW);

        // Then — nothing above it explains row 12
        assertThat(report.verdict()).isEqualTo(Verdict.MATERIAL_DIFFERENCE);
        assertThat(report.lines().get(1).reason()).isEmpty();
    }
}
