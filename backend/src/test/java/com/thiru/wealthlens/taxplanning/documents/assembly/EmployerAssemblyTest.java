package com.thiru.wealthlens.taxplanning.documents.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Several Form No. 130s of one year read together ({@code DR 06} G5.3; W-129, D-754). */
class EmployerAssemblyTest {

    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    /** TY 2026-27's law as the pack states it (`SalaryCertificateLawTest`). */
    private static final SalaryCertificateLaw LAW = new SalaryCertificateLaw(NEW,
            Map.of(NEW, Money.of("75000"), new RegimeCode("FIRST_SCHEDULE_RATES"), Money.of("50000")), Rate.percent("4"));

    private static CertificateLayout.PrintedLine line(String path, String kind, String fact) {
        return new CertificateLayout.PrintedLine(path, Optional.empty(), path, kind, List.of(), false, 1, Optional.of(fact));
    }

    /** Part A's TAN and period; Annexure-I's row A, 1(e) and 4(b) — the lines the assembly finds by fact. */
    private static final CertificateLayout LAYOUT = new CertificateLayout(new CertificateCode("FORM_130"), "T", List.of(),
            List.of(line("a.tan", "TEXT", "EMPLOYER_TAN"), line("a.period", "PERIOD", "EMPLOYMENT_PERIOD"),
                    line("c.rowA", "YES_NO", "REGIME_OPT_OUT"), line("c.row1e", "AMOUNT", "SALARY_FROM_OTHER_EMPLOYERS"),
                    line("c.row4b", "AMOUNT", "STANDARD_DEDUCTION")),
            List.of(), new CertificateLayout.PrintedChecks(List.of(), List.of(), List.of()), "test");

    /** A certificate: its TAN and period, and Part C where {@code partC} holds 1(e) and 4(b) (empty: no Part C). */
    private static ParseAttempt certificate(String document, String tan, String from, String to, Optional<String[]> partC) {
        List<ExtractedField> fields = new ArrayList<>(List.of(
                field("a.tan", new ExtractedValue.TextRead(tan)),
                field("a.period", new ExtractedValue.PeriodRead(LocalDate.parse(from), LocalDate.parse(to)))));
        partC.ifPresent(row -> {
            fields.add(field("c.rowA", new ExtractedValue.FlagRead(false)));
            fields.add(field("c.row1e", new ExtractedValue.MoneyRead(Money.of(row[0]))));
            fields.add(field("c.row4b", new ExtractedValue.MoneyRead(Money.of(row[1]))));
        });
        LocalDateTime at = LocalDateTime.of(2027, 6, 1, 10, 0);
        return new ParseAttempt("attempt-" + document, document, new ParserRun("form-130", "4", at, at), ParseOutcome.SUCCESS, fields,
                List.of(), List.of());
    }

    private static ExtractedField field(String path, ExtractedValue value) {
        return new ExtractedField(path, Optional.of("printed"), Optional.of(value), 0.90, Optional.empty(), Optional.empty());
    }

    private static Optional<String[]> partC(String row1e, String row4b) {
        return Optional.of(new String[] {row1e, row4b});
    }

    @Test
    void assemble_whenEachEmployerGrantsTheWholeStandardDeduction_requiresActionOnTheExcess() {
        // Given — two employers, each with its own Part C and ₹75,000: 75,000 + 75,000 = 1,50,000 against one year's
        // ₹75,000 under the new regime (row A "No"), ₹75,000 over (SAL-STDDED-001/002)
        List<ParseAttempt> year = List.of(certificate("d1", "MUMA12345B", "2026-04-01", "2026-09-30", partC("0", "75000")),
                certificate("d2", "BLRA12345C", "2026-10-01", "2027-03-31", partC("0", "75000")));

        // When
        Assembly assembly = EmployerAssembly.assemble(year, LAYOUT, LAW);

        // Then
        assertThat(assembly.mode()).isEqualTo(AssemblyMode.PER_EMPLOYER);
        assertThat(assembly.notices()).containsExactly(new AssemblyNotice(AssemblyNotice.Level.ACTION_REQUIRED,
                "Each employer gave a standard deduction: ₹1,50,000 in all, where one year allows ₹75,000 under NEW_TAX_REGIME;"
                        + " ₹75,000 more is taxable at filing",
                List.of("d1", "d2"), List.of("SAL-STDDED-001", "SAL-STDDED-002")));
    }

    @Test
    void assemble_whenTheEmployersStandardDeductionsAddUpToOneYears_saysNothing() {
        // Given — 40,000 + 35,000 = 75,000, one year's under the new regime
        List<ParseAttempt> year = List.of(certificate("d1", "MUMA12345B", "2026-04-01", "2026-09-30", partC("0", "40000")),
                certificate("d2", "BLRA12345C", "2026-10-01", "2027-03-31", partC("0", "35000")));

        // When / Then
        assertThat(EmployerAssembly.assemble(year, LAYOUT, LAW).notices()).isEmpty();
    }

    @Test
    void assemble_whenOnlyTheLastEmployerIssuesPartCAndRow1eIsNil_requiresActionOnThePreviousSalary() {
        // Given — DOC-F130-ANNEX-001: one Part C beside two Part A/Bs; its 1(e) prints 0
        List<ParseAttempt> year = List.of(certificate("d1", "MUMA12345B", "2026-04-01", "2026-09-30", Optional.empty()),
                certificate("d2", "BLRA12345C", "2026-10-01", "2027-03-31", partC("0", "75000")));

        // When
        Assembly assembly = EmployerAssembly.assemble(year, LAYOUT, LAW);

        // Then
        assertThat(assembly.mode()).isEqualTo(AssemblyMode.LAST_EMPLOYER_CONSOLIDATED);
        assertThat(assembly.notices()).containsExactly(new AssemblyNotice(AssemblyNotice.Level.ACTION_REQUIRED,
                "Only one employer issued Part C, and its salary from other employers is nil: a previous employer's salary"
                        + " appears not to have been declared",
                List.of("d2"), List.of("TDS-SAL-12B-001")));
    }

    @Test
    void assemble_whenTheLastEmployerDeclaresThePreviousSalary_saysNothing() {
        // Given — 1(e) prints the first employer's ₹3,00,000
        List<ParseAttempt> year = List.of(certificate("d1", "MUMA12345B", "2026-04-01", "2026-09-30", Optional.empty()),
                certificate("d2", "BLRA12345C", "2026-10-01", "2027-03-31", partC("300000", "75000")));

        // When / Then
        assertThat(EmployerAssembly.assemble(year, LAYOUT, LAW).notices()).isEmpty();
    }

    @Test
    void assemble_whenSomeButNotAllOfSeveralEmployersIssuePartC_isMixed() {
        // Given — three Part A/Bs, two Part Cs
        List<ParseAttempt> year = List.of(certificate("d1", "MUMA12345B", "2026-04-01", "2026-07-31", partC("0", "75000")),
                certificate("d2", "BLRA12345C", "2026-08-01", "2026-11-30", Optional.empty()),
                certificate("d3", "DELA12345D", "2026-12-01", "2027-03-31", partC("0", "75000")));

        // When
        Assembly assembly = EmployerAssembly.assemble(year, LAYOUT, LAW);

        // Then — M4.2: CANNOT_COMPARE is W-130's to say; nothing is summed across a mixed year
        assertThat(assembly.mode()).isEqualTo(AssemblyMode.MIXED);
        assertThat(assembly.notices()).isEmpty();
    }

    @Test
    void assemble_whenTheEmployersPeriodsLeaveAGap_asksForAttention() {
        // Given — nothing covers 01-07-2026 to 31-07-2026
        List<ParseAttempt> year = List.of(certificate("d1", "MUMA12345B", "2026-04-01", "2026-06-30", partC("0", "40000")),
                certificate("d2", "BLRA12345C", "2026-08-01", "2027-03-31", partC("0", "35000")));

        // When / Then
        assertThat(EmployerAssembly.assemble(year, LAYOUT, LAW).notices()).containsExactly(new AssemblyNotice(
                AssemblyNotice.Level.ATTENTION, "No certificate covers 01/07/2026 to 31/07/2026: unemployed, or a certificate not uploaded",
                List.of("d1", "d2"), List.of()));
    }

    @Test
    void assemble_whenTwoEmployersPeriodsOverlap_asksForAttention() {
        // Given — C2a: 01-10-2026 to 31-10-2026 under both; two jobs at once is lawful
        List<ParseAttempt> year = List.of(certificate("d1", "MUMA12345B", "2026-04-01", "2026-10-31", partC("0", "40000")),
                certificate("d2", "BLRA12345C", "2026-10-01", "2027-03-31", partC("0", "35000")));

        // When / Then
        assertThat(EmployerAssembly.assemble(year, LAYOUT, LAW).notices()).containsExactly(new AssemblyNotice(
                AssemblyNotice.Level.ATTENTION, "MUMA12345B and BLRA12345C both cover 01/10/2026 to 31/10/2026: two employments at once,"
                        + " or a revised certificate",
                List.of("d1", "d2"), List.of()));
    }

    @Test
    void assemble_whenOneEmployerCoversTheYear_isPerEmployerWithNothingToSay() {
        // When
        Assembly assembly = EmployerAssembly.assemble(List.of(certificate("d1", "MUMA12345B", "2026-04-01", "2027-03-31",
                partC("0", "75000"))), LAYOUT, LAW);

        // Then
        assertThat(assembly.mode()).isEqualTo(AssemblyMode.PER_EMPLOYER);
        assertThat(assembly.notices()).isEmpty();
    }
}
