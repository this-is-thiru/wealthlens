package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Form 16 for TY 2025-26 read by the reader Form No. 130 uses (W-126e2), against W-126e1's layout: Part A's tables, Part B
 * (Annexure-I), Annexure II and the sums the form prints.
 */
class Form16ParserTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TaxYearId.of(2025), TaxYearId.of(2026))));
    private static final TaxYearId YEAR = TaxYearId.of(2025);
    private static final CertificateLayout LAYOUT = CALCULATOR.certificate(YEAR, new CertificateCode("FORM_16")).orElseThrow();
    private static final Form16Parser PARSER = new Form16Parser(CALCULATOR);
    private static final Map<String, String> VALUES = Map.ofEntries(
            Map.entry("partA.summary.row1.quarter", "Q1"),
            Map.entry("partA.summary.row1.receiptNumbers", "QWERTYUI"),
            Map.entry("partA.summary.row1.amountPaid", "3,00,000.00"),
            Map.entry("partA.summary.row1.taxDeducted", "15,000.00"),
            Map.entry("partA.summary.row1.taxDeposited", "15,000.00"),
            Map.entry("partA.summary.total.amountPaid", "3,00,000.00"),
            Map.entry("partA.summary.total.taxDeducted", "15,000.00"),
            Map.entry("partA.summary.total.taxDeposited", "15,000.00"),
            Map.entry("partB.annexureI.rowA", "No"),
            Map.entry("partB.annexureI.row1a", "12,00,000.00"),
            Map.entry("partB.annexureI.row10a", "1,50,000.00 1,50,000.00"),
            Map.entry("partB.annexureI.row21", "0.00"));
    /** An issuer prints "Chapter VI-A" with an ASCII hyphen; the Gazette's text layer carries U+2010 (W-126e1). */
    private static final Map<String, String> ASCII_HYPHEN = Map.of(
            "partB.annexureI.row10m", "Amount deductible under any other provision(s) of Chapter VI-A",
            "partB.annexureI.row10n", "Total of amount deductible under any other provision(s) of Chapter VI-A",
            "partB.annexureII.row8j", "Amount deductible under any other provision(s) of Chapter VI-A",
            "partB.annexureII.row8k", "Total of amount deductible under any other provision(s) of Chapter VI-A");

    private static Map<String, ExtractedField> byPath(DocumentReading reading) {
        return reading.extractedFields().stream().collect(Collectors.toMap(ExtractedField::fieldPath, Function.identity()));
    }

    private static DocumentReading read(Map<String, String> values, Set<String> omitted) throws Exception {
        return PARSER.read(new DocumentContent(CertificateFixture.certificate(LAYOUT, values, ASCII_HYPHEN, omitted)), YEAR);
    }

    @Test
    void read_whenAWholeCertificate_findsEveryLineAndTableWithItsPrintedValue() throws Exception {
        // When
        DocumentReading reading = read(VALUES, Set.of());

        // Then — L2 at 0.90: "12,00,000.00" is ₹12,00,000; Part A's summary row and its Total under their headings
        assertThat(reading.parserNotes()).isEmpty();
        assertThat(reading.unmappedText()).isEmpty();
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields.get("partB.annexureI.row1a").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("1200000")));
        assertThat(fields.get("partB.annexureI.row10a.deductibleAmount").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("150000")));
        assertThat(fields.get("partB.annexureI.rowA").interpretedValue()).contains(new ExtractedValue.FlagRead(false));
        assertThat(fields.get("partA.summary.row1.amountPaid").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("300000")));
        assertThat(fields.get("partA.summary.row1.receiptNumbers").rawText()).contains("QWERTYUI");
        assertThat(fields.get("partA.summary.total.taxDeducted").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("15000")));
    }

    @Test
    void read_whenChapterViAIsPrintedWithAnAsciiHyphen_findsTheFourRowsTheGazettePrintsWithU2010() throws Exception {
        // When
        Map<String, ExtractedField> fields = byPath(read(VALUES, Set.of()));

        // Then — printed blank, each is one field at its line's path
        assertThat(fields).containsKeys("partB.annexureI.row10m", "partB.annexureI.row10n", "partB.annexureII.row8j",
                "partB.annexureII.row8k");
    }

    @Test
    void read_whenEachTablesTotalRowPrintsItsWholeLabel_readsTheTotalsOfAllThree() throws Exception {
        // Given — the Total labels "Total (Rs.)", "Total (Rs. )" and "Total ( Rs.)" (pp.28 – 29)
        Map<String, String> values = new HashMap<>(VALUES);
        values.put("partA.bookAdjustment.row1.serialNumber", "1");
        values.put("partA.bookAdjustment.total.taxDeposited", "5,000.00");
        values.put("partA.challan.row1.serialNumber", "1");
        values.put("partA.challan.total.taxDeposited", "10,000.00");

        // When
        DocumentReading reading = read(values, Set.of());

        // Then
        assertThat(reading.parserNotes()).isEmpty();
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields.get("partA.bookAdjustment.total.taxDeposited").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("5000")));
        assertThat(fields.get("partA.challan.total.taxDeposited").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("10000")));
    }

    @Test
    void read_whenRows19And20AreFromForm12BaaAndRow21IsBlank_infersTheNetTaxPayable() throws Exception {
        // Given — G.S.R. 639(E)'s rows: 21 = 17 - 18 - 19 - 20 = 1,20,000 - 0 - 20,000 - 5,000 = 95,000
        Map<String, String> values = new HashMap<>(VALUES);
        values.remove("partB.annexureI.row21");
        values.put("partB.annexureI.row17", "1,20,000.00");
        values.put("partB.annexureI.row18", "0.00");
        values.put("partB.annexureI.row19", "20,000.00");
        values.put("partB.annexureI.row20", "5,000.00");

        // When
        DocumentReading reading = read(values, Set.of());

        // Then
        assertThat(byPath(reading).get("partB.annexureI.row21").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("95000")));
        assertThat(reading.parserNotes()).containsExactly(
                "Inferred partB.annexureI.row21 from the sum printed for partB.annexureI.row21");
    }

    @Test
    void read_whenASpecifiedBankIssuesAnnexureII_infersRows4And7AndLeavesRow17Alone() throws Exception {
        // Given — a pensioner's certificate: 4 = 3(a) + 3(b) = 50,000 + 0 = 50,000; 7 = 5 + 6 = 5,50,000 + 40,000 =
        // 5,90,000; row 17 prints "(16-17)", so it has no sum (D-740 C5a) and stays blank
        Map<String, String> values = Map.ofEntries(
                Map.entry("partB.annexureII.rowA", "No"),
                Map.entry("partB.annexureII.row1a", "6,00,000.00"),
                Map.entry("partB.annexureII.row2", "6,00,000.00"),
                Map.entry("partB.annexureII.row3a", "50,000.00"),
                Map.entry("partB.annexureII.row3b", "0.00"),
                Map.entry("partB.annexureII.row5", "5,50,000.00"),
                Map.entry("partB.annexureII.row6", "40,000.00"),
                Map.entry("partB.annexureII.row15", "10,000.00"),
                Map.entry("partB.annexureII.row16", "0.00"));

        // When
        DocumentReading reading = read(values, Set.of("partB.annexureI"));

        // Then
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields.get("partB.annexureII.row4").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("50000")));
        assertThat(fields.get("partB.annexureII.row7").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("590000")));
        assertThat(fields.get("partB.annexureII.row17").interpretedValue()).isEmpty();
        assertThat(reading.parserNotes()).containsExactly("PART B (Annexure-I) is not in this certificate",
                "Inferred partB.annexureII.row4 from the sum printed for partB.annexureII.row4",
                "Inferred partB.annexureII.row7 from the sum printed for partB.annexureII.row7");
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
    }

    @Test
    void read_whenTheBytesAreNotAPdf_failsSayingSo() {
        // When
        DocumentReading reading = PARSER.read(new DocumentContent("not a pdf".getBytes(StandardCharsets.US_ASCII)), YEAR);

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.FAILED);
        assertThat(reading.parserNotes()).singleElement().asString().startsWith("Not a PDF");
    }

    @Test
    void read_whenThePageIsAnImageOnly_failsForWantOfText() throws Exception {
        // When
        DocumentReading reading = PARSER.read(new DocumentContent(CertificateFixture.scanned()), YEAR);

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.FAILED);
        assertThat(reading.parserNotes()).singleElement().asString().contains("no text layer");
    }

    @Test
    void read_whenTheYearIsTaxYear2026_27_failsSinceTheRules2026PrescribeFormNo130() throws Exception {
        // Given
        byte[] pdf = CertificateFixture.certificate(LAYOUT, VALUES, ASCII_HYPHEN, Set.of());

        // When — the 1962 form ends with TY 2025-26 (D-741)
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), TaxYearId.of(2026));

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.FAILED);
        assertThat(reading.parserNotes()).containsExactly("No Form 16 layout is in force in 2026-27");
    }

    /** Part A's boxes as an issuer fills them (p.28): every box, an address over two lines, CIT (TDS) by its captions. */
    private static final Map<String, String> BOXES = Map.ofEntries(
            Map.entry("partA.certificate.row1.certificateNo", "QWERTYU"),
            Map.entry("partA.certificate.row1.lastUpdatedOn", "01-Jun-2026"),
            Map.entry("partA.parties.row1.employer", "ACME LTD<br/>1 MG ROAD, BENGALURU"),
            Map.entry("partA.parties.row1.employee", "ASHA RAO<br/>2 PARK ST, KOLKATA"),
            Map.entry("partA.identifiers.row1.deductorPan", "AAACA1234B"),
            Map.entry("partA.identifiers.row1.deductorTan", "BLRA12345C"),
            Map.entry("partA.identifiers.row1.employeePan", "ABCPR1234D"),
            Map.entry("partA.identifiers.row1.employeeReference", "E-77"),
            Map.entry("partA.period.row1.citTds.address", "5th Floor, BMTC Building"),
            Map.entry("partA.period.row1.citTds.city", "BENGALURU"),
            Map.entry("partA.period.row1.citTds.pinCode", "560095"),
            Map.entry("partA.period.row1.assessmentYear", "2026-27"),
            Map.entry("partA.period.row1.from", "01-Apr-2025"),
            Map.entry("partA.period.row1.to", "31-Mar-2026"));

    @Test
    void read_whenPartAsBoxesAreFilled_readsEachBoxUnderItsHeadingAndCitTdsByItsCaptions() throws Exception {
        // Given
        Map<String, String> values = new HashMap<>(VALUES);
        values.putAll(BOXES);

        // When
        DocumentReading reading = read(values, Set.of());

        // Then — each box's printed text, the two-line addresses joined with a space
        assertThat(reading.parserNotes()).isEmpty();
        assertThat(reading.unmappedText()).isEmpty();
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        Map<String, ExtractedField> fields = byPath(reading);
        BOXES.forEach((path, printed) -> assertThat(fields.get(path).interpretedValue()).as(path)
                .contains(new ExtractedValue.TextRead(printed.replace("<br/>", " "))));
        assertThat(fields).doesNotContainKey("partA.period.row1.citTds");
    }
}
