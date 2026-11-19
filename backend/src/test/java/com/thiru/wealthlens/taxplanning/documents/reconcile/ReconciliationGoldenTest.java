package com.thiru.wealthlens.taxplanning.documents.reconcile;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedComparison;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import com.thiru.wealthlens.taxplanning.documents.assembly.Assembly;
import com.thiru.wealthlens.taxplanning.documents.assembly.EmployerAssembly;
import com.thiru.wealthlens.taxplanning.documents.ingest.ConfirmedCertificate;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code DR 08} T8, the Form No. 130 golden cases (W-132 C1a), on the shipped TY 2026-27 {@code FORM_130} layout, each against a
 * real engine computation: T8-1, T8-4 and T8-6 of the T8-1 persona (Priya, {@code Fixtures.priya()}); T8-2 and T8-3 of one employer
 * under the old regime, where their claims are allowed, the engine's deductions checked against figures worked by hand first.
 * T8-5 is the parser's: {@code Form130ParserTest}'s T8-5.
 */
class ReconciliationGoldenTest {

    private static final TaxYearId YEAR = TaxYearId.of(2026);
    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(YEAR)));
    private static final CertificateLayout LAYOUT = CALCULATOR.certificate(YEAR, new CertificateCode("FORM_130")).orElseThrow();
    private static final SalaryCertificateLaw LAW = CALCULATOR.salaryCertificateLaw(YEAR);
    /** The regime row A's Yes leaves for, under which T8-2 and T8-3's Chapter VIII claims are allowed. */
    private static final String OLD = "FIRST_SCHEDULE_RATES";
    /** T8-1's persona, computed by the engine: total income 20,25,000; tax 2,06,250; cess 8,250; the bill 2,14,500. */
    private static final ComputedTax PRIYA = CALCULATOR.compute(Fixtures.priya());

    private static String path(String fact) {
        return LAYOUT.lines().stream().filter(line -> line.fact().filter(fact::equals).isPresent()).findFirst().orElseThrow().path();
    }

    private static String compared(String total) {
        return LAYOUT.checks().comparisons().stream().filter(comparison -> comparison.total().filter(total::equals).isPresent()
                && comparison.reason().isEmpty()).findFirst().map(PrintedComparison::printed).orElseThrow();
    }

    private static String partB() {
        return LAYOUT.checks().comparisons().stream().filter(comparison -> comparison.reason().filter("TAXSUM"::equals).isPresent())
                .findFirst().orElseThrow().printed();
    }

    private static String byNode(String node) {
        return LAYOUT.checks().comparisons().stream().filter(comparison -> comparison.node().filter(node::equals).isPresent())
                .findFirst().orElseThrow().printed();
    }

    private static Map<String, ExtractedValue> partA(String tan, String from, String to, String taxDeducted) {
        Map<String, ExtractedValue> values = new HashMap<>();
        values.put(path("EMPLOYEE_PAN"), new ExtractedValue.TextRead("ABCDE1234F"));
        values.put(path("EMPLOYER_TAN"), new ExtractedValue.TextRead(tan));
        values.put(path("EMPLOYMENT_PERIOD"), new ExtractedValue.PeriodRead(LocalDate.parse(from), LocalDate.parse(to)));
        values.put(partB(), money(taxDeducted));
        return values;
    }

    /** Part C's row A (No: the new regime), 1(e) and 4(b). */
    private static Map<String, ExtractedValue> withPartC(Map<String, ExtractedValue> values, boolean optsOut, String row1e, String row4b) {
        values.put(path("REGIME_OPT_OUT"), new ExtractedValue.FlagRead(optsOut));
        values.put(path("SALARY_FROM_OTHER_EMPLOYERS"), money(row1e));
        values.put(path("STANDARD_DEDUCTION"), money(row4b));
        return values;
    }

    private static ExtractedValue money(String rupees) {
        return new ExtractedValue.MoneyRead(Money.of(rupees));
    }

    private static ConfirmedCertificate certificate(String name, Map<String, ExtractedValue> values) {
        return new ConfirmedCertificate("document-" + name, new DocumentConfirmation("attempt-" + name, "user",
                LocalDateTime.of(2027, 6, 1, 10, 0), values, Set.of()));
    }

    private static ReconciliationReport reconcile(List<ConfirmedCertificate> year, ComputedTax computed) {
        Assembly assembly = EmployerAssembly.assemble(year, LAYOUT, LAW);
        return Reconciliation.reconcile(year, assembly, LAYOUT, computed, LAW);
    }

    private static ReconciliationLine line(ReconciliationReport report, String printed) {
        return report.lines().stream().filter(line -> line.printed().equals(printed)).findFirst().orElseThrow();
    }

    /**
     * One employer's basic pay for the year under the old regime, with what {@code entered} adds, computed by the engine: the
     * T8-1 persona's assessee, family and filing, so only the pay, the claims and the regime differ.
     */
    private static ComputedTax underTheOldRegime(String basic, java.util.function.UnaryOperator<EnteredFacts> entered) {
        EngineInputs persona = Fixtures.priya();
        EnteredFacts facts = entered.apply(persona.entered().withEmployments(List.of(new Employment(
                List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of(basic))), false, EmployerKind.OTHER, false, Optional.empty()))));
        return CALCULATOR.compute(new EngineInputs(persona.taxYear(), new RegimeCode(OLD), persona.temporal(), facts, persona.assessee(),
                persona.family(), persona.ledgers(), persona.elections(), persona.capitalGains(), persona.filing()));
    }

    @Test
    @DisplayName("T8-1 — two employers, both granting the standard deduction")
    void reconcile_whenTwoEmployersEachGrantTheStandardDeduction_isMaterialByTheWholeBillForBothEmployersErrors() {
        // Given — A (April – September) and B (October – March), each a Part C with 1(e) nil and 4(b) 75,000, each deducting 0:
        // A's 9,00,000 − 75,000 = 8,25,000 and B's 12,00,000 − 75,000 = 11,25,000 are each within the s.156 rebate
        List<ConfirmedCertificate> year = List.of(
                certificate("a", withPartC(partA("MUMA12345B", "2026-04-01", "2026-09-30", "0"), false, "0", "75000")),
                certificate("b", withPartC(partA("BLRA12345C", "2026-10-01", "2027-03-31", "0"), false, "0", "75000")));

        // When
        ReconciliationReport report = reconcile(year, PRIYA);

        // Then — 21,00,000 − 75,000 = 20,25,000; 2,00,000 + 25,000 × 0.25 = 2,06,250; cess 8,250; 2,14,500 owed, 0 deducted
        assertThat(PRIYA.figures().bill()).isEqualTo(Money.of("214500"));
        assertThat(report.verdict()).isEqualTo(Verdict.MATERIAL_DIFFERENCE);
        ReconciliationLine deducted = line(report, partB());
        assertThat(deducted.difference()).isEqualTo(Money.of("-214500"));
        assertThat(deducted.reasons()).contains(ReconciliationReason.STANDARD_DEDUCTION_TWICE, ReconciliationReason.PREVIOUS_SALARY_UNDECLARED);
        assertThat(report.explanations()).contains("Each employer granted a whole year's standard deduction, where the year allows one: too little tax was deducted",
                "An employer was not told of the salary from an earlier employer, so it deducted tax as if its own salary were the year's");
    }

    @Test
    @DisplayName("T8-2 — declared but unproven")
    void reconcile_whenAClaimWasDeclaredButNotProven_isMinorAndWarnsOfMoreTaxAtFiling() {
        // Given — basic pay 12,00,000 under the old regime, 60,000 of tuition fees proven under s.123. By hand: 12,00,000 less the
        // old regime's standard deduction 50,000 less 60,000 is a total income of 10,90,000. The employer was told of 1,50,000,
        // so its row 11 prints 1,50,000 and its row 12 12,00,000 − 50,000 − 1,50,000 = 10,00,000; row A Yes, as computed
        ComputedTax computed = underTheOldRegime("1200000", facts -> facts.withQualifyingPayments(List.of(
                new QualifyingPayment(ComponentCodes.TUITION_FEES, Money.of("60000")))));
        assertThat(computed.figures().chapterEight()).isEqualTo(Money.of("60000"));
        assertThat(computed.figures().totalIncome()).isEqualTo(Money.of("1090000"));
        Map<String, ExtractedValue> values = withPartC(partA("MUMA12345B", "2026-04-01", "2027-03-31", "100000"), true, "0", "50000");
        values.put(compared("CHAPTER_VIII"), money("150000"));
        values.put(byNode("round.totalincome"), money("1000000"));
        values.put(compared("BILL"), money("100000"));

        // When
        ReconciliationReport report = reconcile(List.of(certificate("a", values)), computed);

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MINOR_DIFFERENCE);
        assertThat(line(report, compared("CHAPTER_VIII")).difference()).isEqualTo(Money.of("90000"));
        assertThat(line(report, compared("CHAPTER_VIII")).reasons()).contains(ReconciliationReason.DECLARED_NOT_PROVEN);
        assertThat(report.explanations()).contains("A claim declared to the employer is not proven, so the computation leaves it out:"
                + " more tax will be payable at filing");
    }

    @Test
    @DisplayName("T8-3 — entitled but never declared")
    void reconcile_whenADeductionWasNeverDeclared_isMinorWithARefundDue() {
        // Given — basic pay 10,50,000 under the old regime and a 25,000 health-insurance premium for the assessee, never told to
        // the employer. By hand: 10,50,000 − 50,000 − 25,000 (s.126's limit for one under 60) = 9,75,000; the employer's row 11
        // prints 0 and its row 12 10,50,000 − 50,000 = 10,00,000
        ComputedTax computed = underTheOldRegime("1050000", facts -> facts.withHealthPayments(List.of(
                new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM, true, List.of(), Money.of("25000"), false, 1))));
        assertThat(computed.figures().chapterEight()).isEqualTo(Money.of("25000"));
        assertThat(computed.figures().totalIncome()).isEqualTo(Money.of("975000"));
        Map<String, ExtractedValue> values = withPartC(partA("MUMA12345B", "2026-04-01", "2027-03-31", "100000"), true, "0", "50000");
        values.put(compared("CHAPTER_VIII"), money("0"));
        values.put(byNode("round.totalincome"), money("1000000"));

        // When
        ReconciliationReport report = reconcile(List.of(certificate("a", values)), computed);

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MINOR_DIFFERENCE);
        assertThat(line(report, compared("CHAPTER_VIII")).difference()).isEqualTo(Money.of("-25000"));
        assertThat(line(report, compared("CHAPTER_VIII")).reasons()).contains(ReconciliationReason.ENTITLED_NOT_DECLARED);
        assertThat(report.explanations()).contains("A deduction the computation allows was never declared to the employer: a refund"
                + " is due at filing");
    }

    @Test
    @DisplayName("T8-4 — the Part B / Part C tax split")
    void reconcile_whenPartBDeductedTheWholeBill_isMatchedAgainstTheBillNotRow13() {
        // Given — one employer for the year, the whole of T8-1's tax deducted: Part B 2,14,500 = rows 13 + 15 + 16 − 14
        // = 2,06,250 + 0 + 8,250 − 0 (row 17); total income 20,25,000, cess 8,250, no surcharge
        Map<String, ExtractedValue> values = withPartC(partA("MUMA12345B", "2026-04-01", "2027-03-31", "214500"), false, "0", "75000");
        values.put(byNode("round.totalincome"), money("2025000"));
        values.put(byNode("tax.surcharge"), money("0"));
        values.put(byNode("tax.cess"), money("8250"));
        values.put(compared("BILL"), money("214500"));

        // When
        ReconciliationReport report = reconcile(List.of(certificate("a", values)), PRIYA);

        // Then
        assertThat(report.verdict()).isEqualTo(Verdict.MATCHED);
        assertThat(line(report, partB()).difference()).isEqualTo(Money.ZERO);
        assertThat(line(report, partB()).reasons()).containsExactly(ReconciliationReason.TAXSUM);
        assertThat(report.explanations()).containsExactly("Part B's tax deducted is tax, surcharge and cess together, so it is compared with the whole bill, not row 13 alone");
    }

    @Test
    @DisplayName("T8-6 — one Part C, its previous salary nil (D-758: material, not CANNOT_COMPARE)")
    void reconcile_whenOnlyTheLastEmployerIssuesPartCAndLeavesOutTheEarlierSalary_isMaterialAndSaysWhy() {
        // Given — A gave Part A and B only; B's Part C prints 1(e) nil, so B taxed 11,25,000 and deducted 0, like A
        List<ConfirmedCertificate> year = List.of(
                certificate("a", partA("MUMA12345B", "2026-04-01", "2026-09-30", "0")),
                certificate("b", withPartC(partA("BLRA12345C", "2026-10-01", "2027-03-31", "0"), false, "0", "75000")));

        // When
        ReconciliationReport report = reconcile(year, PRIYA);

        // Then — 0 + 0 deducted against the bill 2,14,500
        assertThat(report.verdict()).isEqualTo(Verdict.MATERIAL_DIFFERENCE);
        assertThat(line(report, partB()).difference()).isEqualTo(Money.of("-214500"));
        assertThat(line(report, partB()).reasons()).containsExactly(ReconciliationReason.PREVIOUS_SALARY_UNDECLARED);
        assertThat(report.explanations()).containsExactly("An employer was not told of the salary from an earlier employer, so it deducted tax as if its own salary were the year's");
    }
}
