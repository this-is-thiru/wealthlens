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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Form No. 130 read by L1 and L2: every line found by its printed label (W-126b), its value cells read by kind (W-127a). */
class Form130ParserTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TaxYearId.of(2025), TaxYearId.of(2026))));
    private static final TaxYearId YEAR = TaxYearId.of(2026);
    private static final CertificateLayout LAYOUT = CALCULATOR.certificate(YEAR, new CertificateCode("FORM_130")).orElseThrow();
    private static final Form130Parser PARSER = new Form130Parser(CALCULATOR);
    private static final Map<String, String> VALUES = Map.of(
            "partA.certificateNo", "QWERTY1",
            "partA.employer.tan", "MUMA12345B",
            "partA.employee.name", "Asha Rao",
            "partC.annexureI.rowA", "No",
            "partC.annexureI.row1a", "12,00,000.00",
            "partC.annexureI.row1e", "0.00",
            "partC.annexureI.row7a", "15,000.00",
            "partC.annexureI.row10a", "1,50,000.00 1,50,000.00",
            "partC.annexureI.row21", "0.00");

    private static long linesOf(String prefix) {
        return LAYOUT.lines().stream().filter(line -> !line.repeating() && line.path().startsWith(prefix)).count();
    }

    private static Map<String, ExtractedField> byPath(DocumentReading reading) {
        return reading.extractedFields().stream().collect(Collectors.toMap(ExtractedField::fieldPath, Function.identity()));
    }

    @Test
    void read_whenAWholeCertificate_findsEveryLineWithItsPrintedValue() throws Exception {
        // Given
        byte[] pdf = Form130Fixture.certificate(LAYOUT, VALUES, Map.of(), Set.of());

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then — one field a line, 10(a)'s two printed columns two fields
        assertThat(reading.parserNotes()).isEmpty();
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields).hasSize((int) linesOf("") + 1);
        VALUES.entrySet().stream().filter(entry -> !entry.getKey().endsWith("row10a"))
                .forEach(entry -> assertThat(fields.get(entry.getKey()).rawText()).as(entry.getKey()).contains(entry.getValue()));
        assertThat(fields.get("partC.annexureI.row1").rawText()).isEmpty();
        assertThat(fields.get("partC.annexureI.row4c").interpretedValue()).isEmpty();
        assertThat(fields.get("partC.annexureI.row4c").confidence()).isEqualTo(0.95);
        assertThat(fields.get("partA.employer.name").rawText()).isEmpty();
        assertThat(fields.values()).allSatisfy(field -> assertThat(field.location())
                .hasValueSatisfying(place -> assertThat(place.width()).isPositive()));
    }

    @Test
    void read_whenCellsHoldAmountsFlagsAndWords_readsEachByItsKind() throws Exception {
        // Given
        byte[] pdf = Form130Fixture.certificate(LAYOUT, VALUES, Map.of(), Set.of());

        // When
        Map<String, ExtractedField> fields = byPath(PARSER.read(new DocumentContent(pdf), YEAR));

        // Then — L2 at 0.90: "12,00,000.00" is ₹12,00,000; 10(a) prints 1,50,000.00 under each heading; row A "No"
        assertThat(fields.get("partC.annexureI.row1a").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("1200000")));
        assertThat(fields.get("partC.annexureI.row1a").confidence()).isEqualTo(0.90);
        assertThat(fields.get("partC.annexureI.row10a.grossAmount").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("150000")));
        assertThat(fields.get("partC.annexureI.row10a.deductibleAmount").rawText()).contains("1,50,000.00");
        assertThat(fields).doesNotContainKey("partC.annexureI.row10a");
        assertThat(fields.get("partC.annexureI.rowA").interpretedValue()).contains(new ExtractedValue.FlagRead(false));
        assertThat(fields.get("partA.employer.tan").interpretedValue()).contains(new ExtractedValue.TextRead("MUMA12345B"));
    }

    @Test
    void read_whenRow3IsBlankAndItsSumsTermsArePrinted_infersIt() throws Exception {
        // Given — 1(d) 12,00,000.00 and 2(f) 0.00 printed, row 3 blank: 3 = 1(d) - 2(f) = 12,00,000
        Map<String, String> values = new HashMap<>(VALUES);
        values.put("partC.annexureI.row1d", "12,00,000.00");
        values.put("partC.annexureI.row2f", "0.00");

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(Form130Fixture.certificate(LAYOUT, values, Map.of(), Set.of())),
                YEAR);

        // Then
        ExtractedField row3 = byPath(reading).get("partC.annexureI.row3");
        assertThat(row3.interpretedValue()).contains(new ExtractedValue.MoneyRead(Money.of("1200000")));
        assertThat(row3.confidence()).isEqualTo(0.80);
        assertThat(reading.parserNotes()).containsExactly(
                "Inferred partC.annexureI.row3 from the sum printed for partC.annexureI.row3");
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
    }

    @Test
    void read_whenACellIsNotNumbersOnly_keepsItsTextUnreadAndNamesIt() throws Exception {
        // Given
        Map<String, String> values = new HashMap<>(VALUES);
        values.put("partC.annexureI.row4a", "Rs. 2,500");
        byte[] pdf = Form130Fixture.certificate(LAYOUT, values, Map.of(), Set.of());

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then — D-NOSILENT: not read is not zero
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.PARTIAL);
        assertThat(reading.parserNotes()).containsExactly("Not read: partC.annexureI.row4a (Rs. 2,500)");
        ExtractedField field = byPath(reading).get("partC.annexureI.row4a");
        assertThat(field.rawText()).contains("Rs. 2,500");
        assertThat(field.interpretedValue()).isEmpty();
    }

    @Test
    void read_whenOnlyPartAIsIssued_findsPartAAndSaysPartCIsAbsent() throws Exception {
        // Given — Part C Note 1: the last employer may issue Part C alone
        byte[] pdf = Form130Fixture.certificate(LAYOUT, VALUES, Map.of(), Set.of("partC.annexureI", "partC.annexureII"));

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        assertThat(reading.extractedFields()).hasSize((int) linesOf("partA."));
        assertThat(reading.parserNotes()).containsExactly("PART C (Annexure-I) is not in this certificate",
                "PART C (Annexure-II) is not in this certificate");
    }

    @Test
    void read_whenASpecifiedBankIssuesAnnexureII_readsItsRowsAndInfersWhatItsSumsFix() throws Exception {
        // Given — a specified senior citizen's certificate: Part A and Annexure-II (its Note 1); row 6 broken across lines
        // as the Gazette prints it; rows 4 and 7 blank
        Map<String, String> values = Map.of(
                "partA.employer.tan", "MUMA12345B",
                "partC.annexureII.rowA", "No",
                "partC.annexureII.row1a", "6,00,000.00",
                "partC.annexureII.row2", "6,00,000.00",
                "partC.annexureII.row3a", "2,400.00",
                "partC.annexureII.row3b", "50,000.00",
                "partC.annexureII.row5", "5,47,600.00",
                "partC.annexureII.row6", "12,000.00",
                "partC.annexureII.row8a", "1,50,000.00 1,50,000.00");
        Map<String, String> labels = Map.of("partC.annexureII.row6",
                "Interest Income under the head “Other Sources” paid by the speci-<br/>fied bank");
        byte[] pdf = Form130Fixture.certificate(LAYOUT, values, labels, Set.of("partC.annexureI"));

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then — 4 = 3(a) + 3(b) = 2,400 + 50,000 = 52,400; then 7 = 5 + 6 = 5,47,600 + 12,000 = 5,59,600
        // (5 = 2 − 4 = 6,00,000 − 52,400 = 5,47,600 agrees with the printed row 5)
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields).hasSize((int) (linesOf("partA.") + linesOf("partC.annexureII.")) + 1);
        assertThat(fields.get("partC.annexureII.row4").interpretedValue()).contains(new ExtractedValue.MoneyRead(Money.of("52400")));
        assertThat(fields.get("partC.annexureII.row4").confidence()).isEqualTo(0.80);
        assertThat(fields.get("partC.annexureII.row7").interpretedValue()).contains(new ExtractedValue.MoneyRead(Money.of("559600")));
        assertThat(fields.get("partC.annexureII.row6").interpretedValue()).contains(new ExtractedValue.MoneyRead(Money.of("12000")));
        assertThat(fields.get("partC.annexureII.row8a.grossAmount").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("150000")));
        assertThat(fields.get("partC.annexureII.rowA").interpretedValue()).contains(new ExtractedValue.FlagRead(false));
        assertThat(reading.parserNotes()).containsExactlyInAnyOrder("PART C (Annexure-I) is not in this certificate",
                "Inferred partC.annexureII.row4 from the sum printed for partC.annexureII.row4",
                "Inferred partC.annexureII.row7 from the sum printed for partC.annexureII.row7");
    }

    /** Part B's cells as an issuer fills them: two quarters (the second's rate left blank), one book adjustment, two challans. */
    private static final Map<String, String> PART_B = Map.ofEntries(
            Map.entry("partB.summary.row1.quarter", "Q1"), Map.entry("partB.summary.row1.receiptNumbers", "QVBNMAAA"),
            Map.entry("partB.summary.row1.amountPaid", "3,00,000.00"), Map.entry("partB.summary.row1.rate", "10.00"),
            Map.entry("partB.summary.row1.taxDeducted", "30,000.00"), Map.entry("partB.summary.row1.taxDeposited", "30,000.00"),
            Map.entry("partB.summary.row2.quarter", "Q2"), Map.entry("partB.summary.row2.receiptNumbers", "QVBNMBBB"),
            Map.entry("partB.summary.row2.amountPaid", "3,00,000.00"),
            Map.entry("partB.summary.row2.taxDeducted", "30,000.00"), Map.entry("partB.summary.row2.taxDeposited", "30,000.00"),
            Map.entry("partB.summary.total.amountPaid", "6,00,000.00"), Map.entry("partB.summary.total.taxDeducted", "60,000.00"),
            Map.entry("partB.summary.total.taxDeposited", "60,000.00"),
            Map.entry("partB.bookAdjustment.row1.serialNumber", "1"), Map.entry("partB.bookAdjustment.row1.taxDeposited", "30,000.00"),
            Map.entry("partB.bookAdjustment.row1.form137ReceiptNumber", "1234567"),
            Map.entry("partB.bookAdjustment.row1.ddoSerialNumber", "00012"),
            Map.entry("partB.bookAdjustment.row1.transferVoucherDate", "07/07/2026"),
            Map.entry("partB.bookAdjustment.row1.matchingStatus", "F"),
            Map.entry("partB.bookAdjustment.total.taxDeposited", "30,000.00"),
            Map.entry("partB.challan.row1.serialNumber", "1"), Map.entry("partB.challan.row1.taxDeposited", "30,000.00"),
            Map.entry("partB.challan.row1.bsrCode", "0510308"), Map.entry("partB.challan.row1.depositDate", "07/07/2026"),
            Map.entry("partB.challan.row1.challanSerialNumber", "00042"), Map.entry("partB.challan.row1.matchingStatus", "F"),
            Map.entry("partB.challan.row2.serialNumber", "2"), Map.entry("partB.challan.row2.taxDeposited", "30,000.00"),
            Map.entry("partB.challan.row2.bsrCode", "0510308"), Map.entry("partB.challan.row2.depositDate", "07/10/2026"),
            Map.entry("partB.challan.row2.challanSerialNumber", "00043"), Map.entry("partB.challan.row2.matchingStatus", "F"),
            Map.entry("partB.challan.total.taxDeposited", "60,000.00"));

    private static DocumentReading readWithPartB(Map<String, String> changes, Set<String> omitted) throws Exception {
        Map<String, String> values = new HashMap<>(VALUES);
        values.putAll(PART_B);
        values.putAll(changes);
        return PARSER.read(new DocumentContent(Form130Fixture.certificate(LAYOUT, values, Map.of(), omitted)), YEAR);
    }

    @Test
    void read_whenPartBIsFilled_readsEachTablesRowsAndTotalsUnderTheirHeadings() throws Exception {
        // When
        DocumentReading reading = readWithPartB(Map.of(), Set.of());

        // Then — 3,00,000 + 3,00,000 = 6,00,000 paid and 30,000 + 30,000 = 60,000 deducted, as the Total row prints; the
        // merged "Amount paid/credited" and "Rate" total cell is read as the amount paid; Q2's blank rate is no field (C4a)
        assertThat(reading.parserNotes()).isEmpty();
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields.get("partB.summary.row1.amountPaid").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("300000")));
        assertThat(fields.get("partB.summary.row1.amountPaid").confidence()).isEqualTo(0.90);
        assertThat(fields.get("partB.summary.row1.rate").interpretedValue()).contains(new ExtractedValue.TextRead("10.00"));
        assertThat(fields).doesNotContainKey("partB.summary.row2.rate");
        assertThat(fields.get("partB.summary.row2.quarter").rawText()).contains("Q2");
        assertThat(fields.get("partB.summary.total.amountPaid").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("600000")));
        assertThat(fields.get("partB.summary.total.taxDeducted").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("60000")));
        assertThat(fields.get("partB.bookAdjustment.row1.transferVoucherDate").rawText()).contains("07/07/2026");
        assertThat(fields.get("partB.challan.row2.challanSerialNumber").rawText()).contains("00043");
        assertThat(fields.get("partB.challan.total.taxDeposited").interpretedValue())
                .contains(new ExtractedValue.MoneyRead(Money.of("60000")));
        assertThat(fields.keySet().stream().filter(path -> path.startsWith("partB."))).hasSize(PART_B.size());
    }

    @Test
    void read_whenItemIIsNotPrinted_saysSoAndSucceeds() throws Exception {
        // When — Notes 3, 4: a deductor fills item I or item II
        DocumentReading reading = readWithPartB(Map.of(), Set.of("partB.bookAdjustment"));

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        assertThat(reading.parserNotes()).containsExactly("I. Details of tax deducted and deposited in the central government"
                + " account through book adjustment is not in this certificate");
        assertThat(byPath(reading)).containsKey("partB.challan.row1.taxDeposited");
    }

    @Test
    void read_whenPartBIsNotPrinted_saysSoAndSucceeds() throws Exception {
        // When
        DocumentReading reading = readWithPartB(Map.of(), Set.of("partB"));

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        assertThat(reading.parserNotes()).containsExactly("PART B is not in this certificate");
    }

    @Test
    void read_whenATablesTotalRowIsMissing_isPartialNamingIt() throws Exception {
        // When
        DocumentReading reading = readWithPartB(Map.of(), Set.of("partB.challan.total"));

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.PARTIAL);
        assertThat(reading.parserNotes()).containsExactly("Not found: partB.challan.total (Total)");
    }

    @Test
    void read_whenAPartBAmountIsNotNumbersOnly_keepsItUnreadAndNamesIt() throws Exception {
        // When
        DocumentReading reading = readWithPartB(Map.of("partB.challan.row1.taxDeposited", "Rs. 30,000"), Set.of());

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.PARTIAL);
        assertThat(reading.parserNotes()).containsExactly("Not read: partB.challan.row1.taxDeposited (Rs. 30,000)");
        assertThat(byPath(reading).get("partB.challan.row1.taxDeposited").interpretedValue()).isEmpty();
    }

    @Test
    void read_whenEachAnnexurePrintsItsSubHeading_claimsItAsAHeadingLine() throws Exception {
        // Given — p.2276 and p.2279 print "In relation to …" under each annexure's heading, and Annexure-I "Particulars"
        byte[] pdf = Form130Fixture.certificate(LAYOUT, VALUES, Map.of(), Set.of());

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then — nothing left unclaimed inside either annexure
        assertThat(reading.unmappedText()).isEmpty();
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields.get("partC.annexureI.inRelationTo").rawText()).isEmpty();
        assertThat(fields.get("partC.annexureI.inRelationTo").confidence()).isEqualTo(0.95);
        assertThat(fields).containsKeys("partC.annexureI.particulars", "partC.annexureII.inRelationTo");
    }

    @Test
    void read_whenLabelsWrapElsewhereAndQuotesAreStraight_stillFindsThem() throws Exception {
        // Given — a break inside a word, a break elsewhere, and row 7(a) with straight quotes
        Map<String, String> labels = Map.of(
                "partC.annexureI.row1e", "Reported total amount of salary received from other employ-<br/>er(s)",
                "partC.annexureI.row2a", "Travel concession or assistance<br/>under section 11 being of the nature referred to in"
                        + " Schedule III [Table: Sl. No. 8]",
                "partC.annexureI.row7a", "Amount chargeable under any head of income other than \"Salaries\" as per section"
                        + " 392(4)(a)(iv)");

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(Form130Fixture.certificate(LAYOUT, VALUES, labels,
                Set.of())), YEAR);

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        Map<String, ExtractedField> fields = byPath(reading);
        assertThat(fields.get("partC.annexureI.row1e").rawText()).contains("0.00");
        assertThat(fields).containsKey("partC.annexureI.row2a");
        assertThat(fields.get("partC.annexureI.row7a").rawText()).contains("15,000.00");
    }

    @Test
    void read_whenARowIsNotPrinted_isPartialNamingIt() throws Exception {
        // Given
        byte[] pdf = Form130Fixture.certificate(LAYOUT, VALUES, Map.of(), Set.of("partC.annexureI.row4c"));

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.PARTIAL);
        assertThat(reading.parserNotes()).containsExactly(
                "Not found: partC.annexureI.row4c (Gratuity under section 19(1) [Table: Sl. No.3,4,5,6])");
        assertThat(reading.extractedFields()).hasSize((int) linesOf("") - 1 + 1); // 4(c) missing, 10(a) two columns
    }

    @Test
    void read_whenTheCertificateIsNotAForm130_fails() throws Exception {
        // Given — no PART A heading
        byte[] pdf = Form130Fixture.certificate(LAYOUT, VALUES, Map.of(), Set.of("partA"));

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.FAILED);
        assertThat(reading.parserNotes()).containsExactly("Not a Form No. 130: no PART A");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password-rc4.pdf", "password-aes.pdf"})
    void read_whenThePdfNeedsAPassword_failsSayingSo(String name) throws Exception {
        // Given
        byte[] pdf = Form130Fixture.withPassword(name);

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then — DR 13 row 71
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.FAILED);
        assertThat(reading.parserNotes()).singleElement().asString().contains("password");
    }

    @Test
    void read_whenThePdfIsProtectedOnlyAgainstEditing_readsIt() throws Exception {
        // Given — a whole certificate printing 12,00,000.00 in row 1(a), AES-encrypted with an owner password and no user
        // password by pypdf 6.14.0: it opens without one, as an issuer's protected copy may
        byte[] pdf = Form130Fixture.withPassword("owner-only.pdf");

        // When
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), YEAR);

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        assertThat(byPath(reading).get("partC.annexureI.row1a").rawText()).contains("12,00,000.00");
    }

    @Test
    void read_whenThePageIsAnImageOnly_failsForWantOfText() throws Exception {
        // When
        DocumentReading reading = PARSER.read(new DocumentContent(Form130Fixture.scanned()), YEAR);

        // Then — DR 13 row 72
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.FAILED);
        assertThat(reading.parserNotes()).singleElement().asString().contains("no text layer");
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
    void read_whenTheYearPrescribesNoForm130_failsSayingSo() throws Exception {
        // Given
        byte[] pdf = Form130Fixture.certificate(LAYOUT, VALUES, Map.of(), Set.of());

        // When — TY 2025-26's pack has no FORM_130 (W-126a)
        DocumentReading reading = PARSER.read(new DocumentContent(pdf), TaxYearId.of(2025));

        // Then
        assertThat(reading.outcome()).isEqualTo(ParseOutcome.FAILED);
        assertThat(reading.parserNotes()).containsExactly("No Form No. 130 layout is in force in 2025-26");
    }
}
