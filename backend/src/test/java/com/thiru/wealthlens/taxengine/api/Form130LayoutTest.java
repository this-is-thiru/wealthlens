package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Form No. 130's layout as the shipped TY 2026-27 pack holds it (W-126a), against the labels recorded from the Gazette in
 * {@code docs/taxplanning-build/sources/2026-11-16-form-130.md} — Income-tax Rules, 2026, Appendix III, pp.2274 – 2278.
 */
class Form130LayoutTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TaxYearId.of(2025), TaxYearId.of(2026))));
    private static final CertificateCode FORM_130 = new CertificateCode("FORM_130");

    /** Part A, p.2274: each item by its path, the words of its label. */
    private static final Map<String, String> PART_A = Map.ofEntries(
            Map.entry("partA.certificateNo", "Certificate No."),
            Map.entry("partA.lastUpdatedOn", "Last updated on"),
            Map.entry("partA.employer.name", "Name"),
            Map.entry("partA.employer.address", "Address"),
            Map.entry("partA.employer.pan", "Permanent Account Number"),
            Map.entry("partA.employer.tan", "Tax Deduction and Collection Account Number"),
            Map.entry("partA.employer.email", "Email id"),
            Map.entry("partA.employer.contact", "Contact number"),
            Map.entry("partA.employer.taxYear", "Tax year"),
            Map.entry("partA.employer.period", "Period with the employer"),
            Map.entry("partA.employee.name", "Name"),
            Map.entry("partA.employee.address", "Address"),
            Map.entry("partA.employee.pan", "Permanent Account Number"));

    /** Part C (Annexure-I), pp.2276 – 2278: each row by its printed number, the words of its label. */
    private static final List<List<String>> ANNEXURE_I = List.of(
            List.of("A", "Whether opting out of taxation under section 202(1)?"),
            List.of("1", "Gross Salary"),
            List.of("1(a)", "Salary as per provisions contained in section 16"),
            List.of("1(b)", "Value of perquisites under section 17 (as per Form No. 123, wherever applicable)"),
            List.of("1(c)", "Profits in lieu of salary under section 18 (as per Form No. 123, wherever applicable)"),
            List.of("1(d)", "Total"),
            List.of("1(e)", "Reported total amount of salary received from other employer(s)"),
            List.of("2", "Allowances to the exempt under section 11"),
            List.of("2(a)", "Travel concession or assistance under section 11 being of the nature referred to in Schedule III [Table: Sl. No. 8]"),
            List.of("2(b)", "House rent allowance under section 11 being of the nature referred to in Schedule III [Table: Sl. No. 11]"),
            List.of("2(c)", "Other special allowances under section 11 being of the nature referred to in Schedule III [Table: Sl. No. 12 and 13]"),
            List.of("2(d)", "Amount of any other exemption under section 11"),
            List.of("2(e)", "Total amount of any other exemption under section 11"),
            List.of("2(f)", "Total amount of exemption claimed under section 11 [2(a)+2(b)+2(c)+2(e)]"),
            List.of("3", "Total amount of salary received from current employer [1(d)-2(f)]"),
            List.of("4", "Deductions under section 19(1)"),
            List.of("4(a)", "Tax on employment under section 19(1) [Table: Sl. No.1]"),
            List.of("4(b)", "Standard deduction under section 19(1) [Table: Sl. No.2]"),
            List.of("4(c)", "Gratuity under section 19(1) [Table: Sl. No.3,4,5,6]"),
            List.of("4(d)", "Commuted value of pension under section 19(1) [Table: Sl. No. 7,8,9]"),
            List.of("4(e)", "Compensation as received by workmen under section 19(1) [Table Sl. No. 10,11]"),
            List.of("4(f)", "Amount received on voluntary retirement or termination of service under section 19(1) [Table Sl. No. 12]"),
            List.of("4(g)", "Cash equivalent of leave salary encashment under section 19(1) [Table: Sl. No. 13,14]"),
            List.of("5", "Total amount of deductions under section 19(1) [4(a)+4(b)+4(c) +4(d)+4(e)+ 4(f) +4(g)]"),
            List.of("6", "Income chargeable under the head \"Salaries\" [3+1(e)-5]"),
            List.of("7", "Any other income reported by the employee as per section 392(4)"),
            List.of("7(a)", "Amount chargeable under any head of income other than “Salaries” as per section 392(4)(a)(iv)"),
            List.of("7(b)", "Any loss from house property reported by employee under section 392(4)(a)(iii)"),
            List.of("8", "Total amount of other income reported by the employee [7(a)-7(b)]"),
            List.of("9", "Gross total income (6+8)"),
            List.of("10", "Deductions under Chapter VIII of the Act"),
            List.of("10(a)", "Deduction in respect of life insurance premia, contributions to provident fund, certain pension funds, notified pension schemes etc. under section 123"),
            List.of("10(b)", "Deductions in respect of amount paid/deposited to notified pension scheme under section 124(3)"),
            List.of("10(c)", "Deduction in respect of contribution by Employer to pension scheme under section 124(1)(a)"),
            List.of("10(d)", "Deduction in respect of health insurance premia under section 126"),
            List.of("10(e)", "Deduction in respect of interest on loan taken for higher education under section 129"),
            List.of("10(f)", "Deduction in respect of contribution by the employee to Agnipath Scheme under section 125 (1)"),
            List.of("10(g)", "Deduction in respect of contribution by the Central Government to Agnipath Scheme under section 125 (2)"),
            List.of("10(h)", "Total Deduction in respect of donations to certain funds, charitable institutions, etc. under section 133"),
            List.of("10(i)", "Deduction in respect of interest on deposits in savings account under section 153"),
            List.of("10(j)", "Amounts deductible under any other provisions of Chapter VIII of the Act"),
            List.of("10(k)", "Total of amount deductible under any other provision(s) of Chapter VIII of the Act"),
            List.of("11", "Aggregate of deductible amount under Chapter VIII of the Act [10(a) + 10(b) + 10(c) + 10(d) + 10(e) + 10(f) + 10(g) + 10(h) + 10(i) + 10(k)]"),
            List.of("12", "Total taxable income (9-11)"),
            List.of("13", "Tax on total income"),
            List.of("14", "Rebate under section 156, if applicable"),
            List.of("15", "Surcharge, wherever applicable"),
            List.of("16", "Health and education cess @ 4%"),
            List.of("17", "Tax payable (13+15+16-14)"),
            List.of("18", "Relief under section 157 (attach details)"),
            List.of("19", "Tax deducted at source as per Form No. 122 submitted under provisions of section 392(4)"),
            List.of("20", "Tax collected at source as per Form No. 122 submitted under provisions of section 392(4)"),
            List.of("21", "Net tax payable (17-18-19-20)"));

    @Test
    void certificate_whenTaxYear2026_27_holdsPartAsItemsByTheirPrintedLabels() {
        // When
        CertificateLayout layout = CALCULATOR.certificate(TaxYearId.of(2026), FORM_130).orElseThrow();

        // Then
        Map<String, String> partA = layout.lines().stream().filter(line -> line.path().startsWith("partA."))
                .collect(Collectors.toMap(CertificateLayout.PrintedLine::path, CertificateLayout.PrintedLine::label));
        assertThat(partA).isEqualTo(PART_A);
    }

    @Test
    void certificate_whenTaxYear2026_27_holdsAnnexureIsRowsInPrintedOrder() {
        // When
        CertificateLayout layout = CALCULATOR.certificate(TaxYearId.of(2026), FORM_130).orElseThrow();

        // Then — the repeating "section …" lines carry their parent's row number and are left out here
        List<List<String>> rows = layout.lines().stream().filter(line -> line.path().startsWith("partC.annexureI."))
                .filter(line -> !line.repeating()).map(line -> List.of(line.row().orElseThrow(), line.label())).toList();
        assertThat(rows).containsExactlyElementsOf(ANNEXURE_I);
    }

    @Test
    void certificate_whenTaxYear2026_27_namesTheGazetteAndThePartHeadings() {
        // When
        CertificateLayout layout = CALCULATOR.certificate(TaxYearId.of(2026), FORM_130).orElseThrow();

        // Then
        assertThat(layout.source()).contains("CG-DL-E-20032026-271092").contains("Form No. 130");
        assertThat(layout.parts()).extracting(CertificateLayout.PartHeading::heading).containsExactly("PART A",
                "Details of the employer/specified bank", "Details of the employee/ specified senior citizen",
                "PART C (Annexure-I)");
    }

    @Test
    void certificate_whenTaxYear2026_27_marksTheColumnsAndRepeatingLinesAsPrinted() {
        // When
        Map<String, CertificateLayout.PrintedLine> byPath = CALCULATOR.certificate(TaxYearId.of(2026), FORM_130).orElseThrow()
                .lines().stream().collect(Collectors.toMap(CertificateLayout.PrintedLine::path, line -> line));

        // Then — p.2277: two cells for 10(a) – (g), three from 10(h); p.2274: item 6's two cells
        assertThat(byPath.get("partC.annexureI.row10a").columns()).containsExactly("Gross Amount", "Deductible Amount");
        assertThat(byPath.get("partC.annexureI.row11").columns())
                .containsExactly("Gross Amount", "Qualifying Amount", "Deductible Amount");
        assertThat(byPath.get("partA.employer.contact").columns()).containsExactly("Country Code", "Number");
        assertThat(byPath.get("partC.annexureI.row2d.section").repeating()).isTrue();
        assertThat(byPath.get("partC.annexureI.rowA").kind()).isEqualTo("YES_NO");
        assertThat(byPath.get("partC.annexureI.row1").kind()).isEqualTo("NONE");
    }

    /** The sums pp.2276 – 2278 print, as "target = ±term …", each cell by its line's row id ("Deductible" its column). */
    private static final List<String> SUMS = List.of(
            "2f = +2a +2b +2c +2e (p.2276)",
            "3 = +1d -2f (p.2276)",
            "5 = +4a +4b +4c +4d +4e +4f +4g (p.2277)",
            "6 = +3 +1e -5 (p.2277)",
            "8 = +7a -7b (p.2277)",
            "9 = +6 +8 (p.2277)",
            "11.deductibleAmount = +10a.deductibleAmount +10b.deductibleAmount +10c.deductibleAmount +10d.deductibleAmount"
                    + " +10e.deductibleAmount +10f.deductibleAmount +10g.deductibleAmount +10h.deductibleAmount"
                    + " +10i.deductibleAmount +10k.deductibleAmount (p.2278)",
            "12 = +9 -11.deductibleAmount (p.2278)",
            "17 = +13 +15 +16 -14 (p.2278)",
            "21 = +17 -18 -19 -20 (p.2278)");

    @Test
    void certificate_whenTaxYear2026_27_holdsTheSumsTheFormPrints() {
        // When
        CertificateLayout layout = CALCULATOR.certificate(TaxYearId.of(2026), FORM_130).orElseThrow();

        // Then
        List<String> sums = layout.sums().stream().map(sum -> rowOf(sum.target().path()) + " ="
                + sum.terms().stream().map(term -> " " + (term.sign() > 0 ? "+" : "-") + rowOf(term.path())).collect(Collectors.joining())
                + " (p." + sum.page() + ")").toList();
        assertThat(sums).containsExactlyElementsOf(SUMS);
    }

    private static String rowOf(String path) {
        return path.substring("partC.annexureI.row".length());
    }

    @Test
    void cellPath_whenTheLineHasColumns_isTheLineAndTheHeadingInCamelCase() {
        // When / Then
        assertThat(CertificateLayout.cellPath("partC.annexureI.row10a", java.util.Optional.of("Gross Amount")))
                .isEqualTo("partC.annexureI.row10a.grossAmount");
        assertThat(CertificateLayout.cellPath("partC.annexureI.row3", java.util.Optional.empty())).isEqualTo("partC.annexureI.row3");
    }

    @Test
    void certificate_whenTaxYear2025_26_isEmptySinceTheRules2026PrescribeIt() {
        // When / Then — the Income-tax Rules, 2026 govern TY 2026-27 on (YR-ACT-004); TY 2025-26's is Form 16 (W-126e)
        assertThat(CALCULATOR.certificate(TaxYearId.of(2025), FORM_130)).isEmpty();
    }
}
