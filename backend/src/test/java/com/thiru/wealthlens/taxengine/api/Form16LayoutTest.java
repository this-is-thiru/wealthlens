package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Form 16's layout as the shipped TY 2025-26 pack holds it (W-126e1), against the labels recorded from the Gazette in
 * {@code docs/taxplanning-build/sources/2026-11-17-form-16.md} — Income-tax Rules, 1962, Appendix II, Form No. 16 as
 * substituted by G.S.R. 379(E) (pp.28 – 33) and amended by G.S.R. 639(E) (p.6).
 */
class Form16LayoutTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TaxYearId.of(2025), TaxYearId.of(2026))));
    private static final CertificateCode FORM_16 = new CertificateCode("FORM_16");
    /** The hyphen the Gazette's text layer carries in four "Chapter VI-A" labels (U+2010), kept as printed. */
    private static final String HYPHEN = "\u2010";

    /** Part B (Annexure-I), pp.29 – 31, rows 19 – 21 from G.S.R. 639(E) p.6: each row's number, the words of its label. */
    private static final List<List<String>> ANNEXURE_I = List.of(
            List.of("A", "Whether opting out of taxation u/s 115BAC(1A)?"),
            List.of("1", "Gross Salary"),
            List.of("1(a)", "Salary as per provisions contained in section 17(1)"),
            List.of("1(b)", "Value of perquisites under section 17(2) (as per Form No. 12BA, wherever applicable)"),
            List.of("1(c)", "Profits in lieu of salary under section 17(3) (as per Form No. 12BA, wherever applicable)"),
            List.of("1(d)", "Total"),
            List.of("1(e)", "Reported total amount of salary received from other employer(s)"),
            List.of("2", "Less: Allowances to the extent exempt under section 10"),
            List.of("2(a)", "Travel concession or assistance under section 10(5)"),
            List.of("2(b)", "Death-cum-retirement gratuity under section 10(10)"),
            List.of("2(c)", "Commuted value of pension under section 10(10A)"),
            List.of("2(d)", "Cash equivalent of leave salary encashment under section 10(10AA)"),
            List.of("2(e)", "House rent allowance under section 10(13A)"),
            List.of("2(f)", "Other special allowances under section 10(14)"),
            List.of("2(g)", "Amount of any other exemption under section 10"),
            List.of("2(h)", "Total amount of any other exemption under section 10"),
            List.of("2(i)", "Total amount of exemption claimed under section 10 [2(a)+2(b)+2(c)+2(d)+2(e)+2(f)+2(h)]"),
            List.of("3", "Total amount of salary received from current employer [1(d)-2(i)]"),
            List.of("4", "Less: Deductions under section 16"),
            List.of("4(a)", "Standard deduction under section 16(ia)"),
            List.of("4(b)", "Entertainment allowance under section 16(ii)"),
            List.of("4(c)", "Tax on employment under section 16(iii)"),
            List.of("5", "Total amount of deductions under section 16 [4(a)+4(b)+4(c)]"),
            List.of("6", "Income chargeable under the head \"Salaries\" [(3+1(e)-5]"),
            List.of("7", "Add: Any other income reported by the employee under as per section 192 (2B)"),
            List.of("7(a)", "Income (or admissible loss) from house property reported by employee offered for TDS"),
            List.of("7(b)", "Income under the head other sources offered for TDS"),
            List.of("8", "Total amount of other income reported by the employee [7(a)+7(b)]"),
            List.of("9", "Gross total income (6+8)"),
            List.of("10", "Deductions under Chapter VI-A"),
            List.of("10(a)", "Deduction in respect of life insurance premia, contributions to provident fund etc. under section 80C"),
            List.of("10(b)", "Deduction in respect of contribution to certain pension funds under section 80CCC"),
            List.of("10(c)", "Deduction in respect of contribution by taxpayer to pension scheme under section 80CCD (1)"),
            List.of("10(d)", "Total deduction under section 80C, 80CCC and 80CCD(1)"),
            List.of("10(e)", "Deductions in respect of amount paid/deposited to notified pension scheme under section 80CCD (1B)"),
            List.of("10(f)", "Deduction in respect of contribution by Employer to pension scheme under section 80CCD (2)"),
            List.of("10(g)", "Deduction in respect of health insurance premia under section 80D"),
            List.of("10(h)", "Deduction in respect of interest on loan taken for higher education under section 80E"),
            List.of("10(i)", "Deduction in respect of contribution by the employee to Agnipath Scheme under section 80CCH"),
            List.of("10(j)", "Deduction in respect of contribution by the Central Government to Agnipath Scheme under section 80CCH"),
            List.of("10(k)", "Total Deduction in respect of donations to certain funds, charitable institutions, etc. under section 80G"),
            List.of("10(l)", "Deduction in respect of interest on deposits in savings account under section 80TTA"),
            List.of("10(m)", "Amount deductible under any other provision(s) of Chapter VI" + HYPHEN + "A"),
            List.of("10(n)", "Total of amount deductible under any other provision(s) of Chapter VI" + HYPHEN + "A"),
            List.of("11", "Aggregate of deductible amount under Chapter VI-A [10(d)+10(e)+10(f)+10(g)+10(h)+ 10(i)+10(j) +10(k) +10(l) +10(n)]"),
            List.of("12", "Total taxable income (9-11)"),
            List.of("13", "Tax on total income"),
            List.of("14", "Rebate under section 87A, if applicable"),
            List.of("15", "Surcharge, wherever applicable"),
            List.of("16", "Health and education cess @ 4%"),
            List.of("17", "Tax payable (13+15+16-14)"),
            List.of("18", "Less: Relief under section 89 (attach details)"),
            List.of("19", "Less: Tax deducted at source as per Form No. 12BAA submitted under provisions of section 192(2B)"),
            List.of("20", "Less: Tax collected at source as per Form No. 12BAA submitted under provisions of section 192(2B)"),
            List.of("21", "Net tax payable ( 17-18-19-20)"));

    /** Annexure II, pp.31 – 32. */
    private static final List<List<String>> ANNEXURE_II = List.of(
            List.of("A", "Whether opting out of taxation u/s 115BAC(1A)?"),
            List.of("1", "Gross Salary"),
            List.of("1(a)", "Pension as per provisions contained in clause (ii) of section 17(1)"),
            List.of("2", "Total amount of salary received"),
            List.of("3", "Less: Deductions under section 16"),
            List.of("3(a)", "Standard deduction under section 16(ia)"),
            List.of("3(b)", "Tax on employment under section 16(iii)"),
            List.of("4", "Total amount of deductions under section 16 [3(a)+3(b)]"),
            List.of("5", "Income chargeable under the head \"Salaries\" [(2-4]"),
            List.of("6", "Interest Income under the head “Other Sources” paid by the specified bank"),
            List.of("7", "Gross total income (5+6)"),
            List.of("8", "Deductions under Chapter VI-A"),
            List.of("8(a)", "Deduction in respect of life insurance premia, contributions to provident fund etc. under section 80C"),
            List.of("8(b)", "Deduction in respect of contribution to certain pension funds under section 80CCC"),
            List.of("8(c)", "Deduction in respect of contribution by taxpayer to pension scheme under section 80CCD(1)"),
            List.of("8(d)", "Total deduction under section 80C, 80CCC and 80CCD (1)"),
            List.of("8(e)", "Deductions in respect of amount paid/deposited to notified pension scheme under section 80CCD (1B)"),
            List.of("8(f)", "Deduction in respect of health insurance premia under section 80D"),
            List.of("8(g)", "Deduction in respect of interest on loan taken for higher education under section 80E"),
            List.of("8(h)", "Total Deduction in respect of donations to certain funds, charitable institutions, etc. under section 80G"),
            List.of("8(i)", "Deduction in respect of interest on deposits in savings account under section 80TTB"),
            List.of("8(j)", "Amount deductible under any other provision(s) of Chapter VI" + HYPHEN + "A"),
            List.of("8(k)", "Total of amount deductible under any other provision(s) of Chapter VI" + HYPHEN + "A"),
            List.of("9", "Aggregate of deductible amount under Chapter VI-A [8(d)+8(e)+8(f)+8(g)+8(h)+8(i)+8(k)]"),
            List.of("10", "Total taxable income (7-9)"),
            List.of("11", "Tax on total income"),
            List.of("12", "Rebate under section 87A, if applicable"),
            List.of("13", "Surcharge, wherever applicable"),
            List.of("14", "Health and education cess"),
            List.of("15", "Tax payable (11+13+14-12)"),
            List.of("16", "Less: Relief under section 89 (attach details)"),
            List.of("17", "Net tax payable (16-17)"));

    /** The lines each annexure prints above its rows, with no row number (pp.29, 31). */
    private static final Map<String, String> ANNEXURE_HEADINGS = Map.of(
            "partB.annexureI.inRelationTo", "In relation to employees for tax deduction under section 192",
            "partB.annexureI.details", "Details of salary paid and any other income and tax deducted",
            "partB.annexureII.inRelationTo", "In relation to specified senior citizen for tax deduction under section 194P");

    @Test
    void certificate_whenTaxYear2025_26_holdsAnnexureIsRowsInPrintedOrder() {
        // When / Then — the repeating "clause …" and "section …" lines carry their parent's row number and are left out
        assertThat(rowsOf("partB.annexureI.")).containsExactlyElementsOf(ANNEXURE_I);
    }

    @Test
    void certificate_whenTaxYear2025_26_holdsAnnexureIIsRowsInPrintedOrder() {
        // When / Then
        assertThat(rowsOf("partB.annexureII.")).containsExactlyElementsOf(ANNEXURE_II);
    }

    private static List<List<String>> rowsOf(String part) {
        return CALCULATOR.certificate(TaxYearId.of(2025), FORM_16).orElseThrow().lines().stream()
                .filter(line -> line.path().startsWith(part) && line.row().isPresent() && !line.repeating())
                .map(line -> List.of(line.row().orElseThrow(), line.label())).toList();
    }

    @Test
    void certificate_whenTaxYear2025_26_holdsTheHeadingLinesEachAnnexurePrintsAboveItsRows() {
        // When
        List<CertificateLayout.PrintedLine> headings = CALCULATOR.certificate(TaxYearId.of(2025), FORM_16).orElseThrow()
                .lines().stream().filter(line -> line.path().startsWith("partB.") && line.row().isEmpty()).toList();

        // Then
        assertThat(headings.stream().collect(Collectors.toMap(CertificateLayout.PrintedLine::path,
                CertificateLayout.PrintedLine::label))).isEqualTo(ANNEXURE_HEADINGS);
        assertThat(headings).allSatisfy(line -> assertThat(line.kind()).isEqualTo("NONE"));
    }

    @Test
    void certificate_whenTaxYear2025_26_namesBothNotificationsAndThePartHeadings() {
        // When
        CertificateLayout layout = CALCULATOR.certificate(TaxYearId.of(2025), FORM_16).orElseThrow();

        // Then — Part A's header boxes are W-126e3's, so Part A holds only its tables here
        assertThat(layout.source()).contains("CG-DL-E-22052023-245989").contains("CG-DL-E-15102024-257917")
                .contains("Form No. 16");
        assertThat(layout.parts()).extracting(CertificateLayout.PartHeading::heading)
                .containsExactly("PART A", "PART B (Annexure-I)", "Annexure II");
        assertThat(layout.lines()).noneMatch(line -> line.path().startsWith("partA."));
    }

    @Test
    void certificate_whenTaxYear2025_26_marksTheColumnsAndRepeatingLinesAsPrinted() {
        // When
        Map<String, CertificateLayout.PrintedLine> byPath = CALCULATOR.certificate(TaxYearId.of(2025), FORM_16).orElseThrow()
                .lines().stream().collect(Collectors.toMap(CertificateLayout.PrintedLine::path, line -> line));

        // Then — p.30: two cells for 10(a) – (j); p.31: three from 10(k) to 11
        assertThat(byPath.get("partB.annexureI.row10a").columns()).containsExactly("Gross Amount", "Deductible Amount");
        assertThat(byPath.get("partB.annexureI.row10j").columns()).containsExactly("Gross Amount", "Deductible Amount");
        assertThat(byPath.get("partB.annexureI.row10k").columns())
                .containsExactly("Gross Amount", "Qualifying Amount", "Deductible Amount");
        assertThat(byPath.get("partB.annexureI.row11").columns())
                .containsExactly("Gross Amount", "Qualifying Amount", "Deductible Amount");
        assertThat(byPath.get("partB.annexureI.row2g.clause").repeating()).isTrue();
        assertThat(byPath.get("partB.annexureI.row2g.clause").label()).isEqualTo("clause …");
        assertThat(byPath.get("partB.annexureI.row10m.section").repeating()).isTrue();
        assertThat(byPath.get("partB.annexureI.row10m.section").columns())
                .containsExactly("Gross Amount", "Qualifying Amount", "Deductible Amount");
        assertThat(byPath.get("partB.annexureI.rowA").kind()).isEqualTo("YES_NO");
        assertThat(byPath.get("partB.annexureI.row2g").kind()).isEqualTo("NONE");
        // p.32: two cells for 8(a) – (g), three from 8(h) to 9; 8(j)'s "section …" lines repeat
        assertThat(byPath.get("partB.annexureII.row8g").columns()).containsExactly("Gross Amount", "Deductible Amount");
        assertThat(byPath.get("partB.annexureII.row8h").columns())
                .containsExactly("Gross Amount", "Qualifying Amount", "Deductible Amount");
        assertThat(byPath.get("partB.annexureII.row9").columns())
                .containsExactly("Gross Amount", "Qualifying Amount", "Deductible Amount");
        assertThat(byPath.get("partB.annexureII.row8j.section").repeating()).isTrue();
        assertThat(byPath.get("partB.annexureII.rowA").kind()).isEqualTo("YES_NO");
        assertThat(byPath.get("partB.annexureII.row8j").kind()).isEqualTo("NONE");
    }

    /** The sums pp.30 – 31 print, row 21 as G.S.R. 639(E) p.6 prints it; row 6's "[(3+1(e)-5]" is 3 + 1(e) − 5. */
    private static final List<String> SUMS = List.of(
            "2i = +2a +2b +2c +2d +2e +2f +2h (p.30)",
            "3 = +1d -2i (p.30)",
            "5 = +4a +4b +4c (p.30)",
            "6 = +3 +1e -5 (p.30)",
            "8 = +7a +7b (p.30)",
            "9 = +6 +8 (p.30)",
            "11.deductibleAmount = +10d.deductibleAmount +10e.deductibleAmount +10f.deductibleAmount +10g.deductibleAmount"
                    + " +10h.deductibleAmount +10i.deductibleAmount +10j.deductibleAmount +10k.deductibleAmount"
                    + " +10l.deductibleAmount +10n.deductibleAmount (p.31)",
            "12 = +9 -11.deductibleAmount (p.31)",
            "17 = +13 +15 +16 -14 (p.31)",
            "21 = +17 -18 -19 -20 (p.6)");

    /** The sums pp.31 – 32 print; row 5's "[(2-4]" is 2 − 4. Row 17's "(16-17)" names itself, so it has none (D-740 C5a). */
    private static final List<String> SUMS_II = List.of(
            "4 = +3a +3b (p.31)",
            "5 = +2 -4 (p.31)",
            "7 = +5 +6 (p.32)",
            "9.deductibleAmount = +8d.deductibleAmount +8e.deductibleAmount +8f.deductibleAmount +8g.deductibleAmount"
                    + " +8h.deductibleAmount +8i.deductibleAmount +8k.deductibleAmount (p.32)",
            "10 = +7 -9.deductibleAmount (p.32)",
            "15 = +11 +13 +14 -12 (p.32)");

    @Test
    void certificate_whenTaxYear2025_26_holdsTheSumsTheFormPrints() {
        // When
        CertificateLayout layout = CALCULATOR.certificate(TaxYearId.of(2025), FORM_16).orElseThrow();

        // Then
        assertThat(sumsOf(layout, "partB.annexureI.row")).containsExactlyElementsOf(SUMS);
        assertThat(sumsOf(layout, "partB.annexureII.row")).containsExactlyElementsOf(SUMS_II);
    }

    private static List<String> sumsOf(CertificateLayout layout, String rows) {
        return layout.sums().stream().filter(sum -> sum.target().path().startsWith(rows))
                .map(sum -> rowOf(rows, sum.target().path()) + " =" + sum.terms().stream()
                        .map(term -> " " + (term.sign() > 0 ? "+" : "-") + rowOf(rows, term.path())).collect(Collectors.joining())
                        + " (p." + sum.page() + ")").toList();
    }

    private static String rowOf(String rows, String path) {
        assertThat(path).startsWith(rows);
        return path.substring(rows.length());
    }

    /** Part A, pp.28 – 29: each table's title lines, then each column as "heading → key kind [group]", then its total. */
    private static final Map<String, List<String>> PART_A_TABLES = Map.of(
            "partA.summary", List.of(
                    "Summary of amount paid/credited and tax deducted at source thereon in respect of the employee",
                    "Quarter(s) → quarter TEXT",
                    "Receipt Numbers of original quarterly statement of TDS under sub section (3) of section 200 → receiptNumbers TEXT",
                    "Amount paid/credited → amountPaid AMOUNT",
                    "Amount of tax deducted (Rs.) → taxDeducted AMOUNT",
                    "Amount of tax deposited/ remitted (Rs.) → taxDeposited AMOUNT",
                    "Total (Rs.): [receiptNumbers] [amountPaid] [taxDeducted] [taxDeposited] (p.28)"),
            "partA.bookAdjustment", List.of(
                    "I. DETAILS OF TAX DEDUCTED AND DEPOSITED IN THE CENTRAL GOVERNMENT ACCOUNT THROUGH BOOK ADJUSTMENT",
                    "(The deductor to provide payment wise details of tax deducted and deposited with respect to the deductee)",
                    "Sl. No. → serialNumber TEXT",
                    "Tax Deposited in respect of the deductee (Rs. ) → taxDeposited AMOUNT",
                    "Receipt numbers of Form No. 24G → form24GReceiptNumber TEXT [Book Identification Number (BIN)]",
                    "DDO serial number in Form No. 24G → ddoSerialNumber TEXT [Book Identification Number (BIN)]",
                    "Date of transfer voucher dd/mm/yyyy → transferVoucherDate TEXT [Book Identification Number (BIN)]",
                    "Status of matching with Form No. 24G → matchingStatus TEXT [Book Identification Number (BIN)]",
                    "Total (Rs. ): [taxDeposited] [form24GReceiptNumber] [ddoSerialNumber] [transferVoucherDate] [matchingStatus] (p.28)"),
            "partA.challan", List.of(
                    "II. DETAILS OF TAX DEDUCTED AND DEPOSITED IN THE CENTRAL GOVERNMENT ACCOUNT THROUGH CHALLAN",
                    "(The deductor to provide payment wise details of tax deducted and deposited with respect to the deductee)",
                    "Sl. No. → serialNumber TEXT",
                    "Tax Deposited in respect of the deductee (Rs. ) → taxDeposited AMOUNT",
                    "BSR Code of the Bank Branch → bsrCode TEXT [Challan Identification Number (CIN)]",
                    "Date on which tax deposited (dd/mm/yyyy) → depositDate TEXT [Challan Identification Number (CIN)]",
                    "Challan Serial Number → challanSerialNumber TEXT [Challan Identification Number (CIN)]",
                    "Status of matching With OLTAS → matchingStatus TEXT [Challan Identification Number (CIN)]",
                    "Total ( Rs.): [taxDeposited] [bsrCode] [depositDate] [challanSerialNumber] [matchingStatus] (p.29)"));

    @Test
    void certificate_whenTaxYear2025_26_holdsPartAsTablesAsPrinted() {
        // When
        CertificateLayout layout = CALCULATOR.certificate(TaxYearId.of(2025), FORM_16).orElseThrow();

        // Then — in printed order: the summary, then item I (book adjustment), then item II (challan)
        assertThat(layout.tables()).extracting(CertificateLayout.PrintedTable::path)
                .containsExactly("partA.summary", "partA.bookAdjustment", "partA.challan");
        assertThat(layout.tables().stream().collect(Collectors.toMap(CertificateLayout.PrintedTable::path, table -> {
            List<String> printed = new ArrayList<>(table.title());
            table.columns().forEach(column -> printed.add(column.heading() + " → " + column.key() + " " + column.kind()
                    + column.group().map(group -> " [" + group + "]").orElse("")));
            printed.add(table.totalLabel() + ": " + table.totalCells().stream().map(cell -> cell.toString())
                    .collect(Collectors.joining(" ")) + " (p." + table.page() + ")");
            return printed;
        }))).isEqualTo(PART_A_TABLES);
    }

    @Test
    void certificate_whenTaxYear2026_27_isEmptySinceThe1962RulesEndWithTaxYear2025_26() {
        // When / Then — the 1962 form governs only tax years beginning before 1-4-2026 (D-741); the Rules, 2026 name a
        // donation certificate "Form No. 16"
        assertThat(CALCULATOR.certificate(TaxYearId.of(2026), FORM_16)).isEmpty();
    }
}
