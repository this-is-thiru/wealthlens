package com.thiru.wealthlens.taxengine.rules.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.CertificateFact;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A certificate's layout refuses an impossible state when it is made, whoever makes it (W-126a). */
class CertificateSpecTest {

    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-126a"), "Synthetic",
            LocalDate.of(2026, 11, 16), VerificationLevel.UNVERIFIED, "nothing");
    private static final CertificatePart PART_A = new CertificatePart("partA", "PART A");
    private static final CertificateChecks NO_CHECKS = new CertificateChecks(List.of(), List.of(), List.of());
    private static final CertificateField NAME = new CertificateField("partA.name", Optional.of("1"), "Name",
            CertificateValueKind.TEXT, List.of(), false, 1, Optional.empty());

    @Test
    void newSpec_whenTheTitleIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateSpec(new CertificateCode("SYN_1"), " ", List.of(PART_A), List.of(NAME), List.of(), NO_CHECKS, SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A certificate needs its code, title and provenance");
    }

    @Test
    void newSpec_whenTheProvenanceIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateSpec(new CertificateCode("SYN_1"), "T", List.of(PART_A), List.of(NAME), List.of(), NO_CHECKS, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A certificate needs its code, title and provenance");
    }

    @Test
    void newPart_whenTheHeadingIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificatePart("partA", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A certificate part needs its printed heading: partA");
    }

    @Test
    void newPart_whenThePathIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificatePart(null, "PART A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A certificate part's path is dot-separated words: null");
    }

    @Test
    void newField_whenTheLabelIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.name", Optional.empty(), " ", CertificateValueKind.TEXT,
                List.of(), false, 1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.name needs its row (or none), label, kind and columns");
    }

    @Test
    void newField_whenTheRowIsNullRatherThanEmpty_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.name", null, "Name", CertificateValueKind.TEXT, List.of(),
                false, 1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.name needs its row (or none), label, kind and columns");
    }

    @Test
    void newField_whenTheKindIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.name", Optional.empty(), "Name", null, List.of(), false, 1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.name needs its row (or none), label, kind and columns");
    }

    @Test
    void newField_whenTheColumnsAreNull_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.name", Optional.empty(), "Name", CertificateValueKind.TEXT, null,
                false, 1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.name needs its row (or none), label, kind and columns");
    }

    @Test
    void newField_whenThePathIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField(null, Optional.empty(), "Name", CertificateValueKind.TEXT, List.of(),
                false, 1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A certificate field's path is dot-separated words: null");
    }

    @Test
    void newPart_whenTheHeadingIsNull_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificatePart("partA", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A certificate part needs its printed heading: partA");
    }

    private static final CertificateCell TOTAL = new CertificateCell("partA.total", Optional.empty());

    @Test
    void newCell_whenTheColumnIsNullRatherThanEmpty_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateCell("partA.total", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A certificate cell names its line, and its column or none");
    }

    @Test
    void newCell_whenTheLineIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateCell(null, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A certificate cell names its line, and its column or none");
    }

    @Test
    void newTerm_whenTheCellIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateTerm(null, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A sum's term is a cell with a sign of +1 or -1");
    }

    @Test
    void newIdentity_whenItHasNoTerm_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateIdentity(TOTAL, List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A printed sum needs its target, at least one term and its page");
    }

    @Test
    void newIdentity_whenThePageIsZero_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateIdentity(TOTAL,
                List.of(new CertificateTerm(new CertificateCell("partA.part", Optional.empty()), 1)), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A printed sum needs its target, at least one term and its page");
    }

    @Test
    void newIdentity_whenTheTargetIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateIdentity(null, List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A printed sum needs its target, at least one term and its page");
    }

    @Test
    void newTotal_whenACellSpansNoColumn_refuses() {
        // When / Then
        assertThatThrownBy(() -> new TableTotal("Total", List.of(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A total row needs its printed label and at least one cell, each spanning a column");
    }

    @Test
    void newColumn_whenTheGroupIsNullRatherThanEmpty_refuses() {
        // When / Then
        assertThatThrownBy(() -> new TableColumn("Sl. No.", "serial", CertificateValueKind.TEXT, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Column serial needs its printed heading, its kind and its group or none");
    }

    @Test
    void newTable_whenItHasNoColumn_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateTable("partA.summary", List.of("Summary"), List.of(),
                Optional.of(new TableTotal("Total", List.of(List.of("paid")))), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.summary needs its printed title or none, its columns, its total row or none and its page");
    }

    private static final Optional<TableTotal> ONE_CELL = Optional.of(new TableTotal("Total", List.of(List.of("paid"))));
    private static final TableColumn PAID = new TableColumn("Amount paid", "paid", CertificateValueKind.AMOUNT, Optional.empty(), List.of());

    @Test
    void newColumn_whenAPartIsMissingOrBlank_refuses() {
        // When / Then — the key first, then the heading, the kind and the group
        assertThatThrownBy(() -> new TableColumn("Amount paid", null, CertificateValueKind.AMOUNT, Optional.empty(), List.of()))
                .hasMessage("A table column's key is one word in camel case: null");
        assertThatThrownBy(() -> new TableColumn(null, "paid", CertificateValueKind.AMOUNT, Optional.empty(), List.of()))
                .hasMessage("Column paid needs its printed heading, its kind and its group or none");
        assertThatThrownBy(() -> new TableColumn(" ", "paid", CertificateValueKind.AMOUNT, Optional.empty(), List.of()))
                .hasMessage("Column paid needs its printed heading, its kind and its group or none");
        assertThatThrownBy(() -> new TableColumn("Amount paid", "paid", null, Optional.empty(), List.of()))
                .hasMessage("Column paid needs its printed heading, its kind and its group or none");
        assertThatThrownBy(() -> new TableColumn("Amount paid", "paid", CertificateValueKind.AMOUNT, Optional.of(" "), List.of()))
                .hasMessage("Column paid needs its printed heading, its kind and its group or none");
    }

    @Test
    void newTotal_whenALabelOrCellIsMissing_refuses() {
        // When / Then
        String message = "A total row needs its printed label and at least one cell, each spanning a column";
        assertThatThrownBy(() -> new TableTotal(null, List.of(List.of("paid")))).hasMessage(message);
        assertThatThrownBy(() -> new TableTotal(" ", List.of(List.of("paid")))).hasMessage(message);
        assertThatThrownBy(() -> new TableTotal("Total", null)).hasMessage(message);
        assertThatThrownBy(() -> new TableTotal("Total", List.of())).hasMessage(message);
        assertThatThrownBy(() -> new TableTotal("Total", java.util.Arrays.asList((List<String>) null))).hasMessage(message);
    }

    @Test
    void newTable_whenAPartIsMissingOrBlank_refuses() {
        // When / Then
        String message = "partA.summary needs its printed title or none, its columns, its total row or none and its page";
        assertThatThrownBy(() -> new CertificateTable("PartA.summary", List.of("Summary"), List.of(PAID), ONE_CELL, 1))
                .hasMessage("A certificate table's path is dot-separated words: PartA.summary");
        assertThatThrownBy(() -> new CertificateTable(null, List.of("Summary"), List.of(PAID), ONE_CELL, 1))
                .hasMessage("A certificate table's path is dot-separated words: null");
        assertThatThrownBy(() -> new CertificateTable("partA.summary", null, List.of(PAID), ONE_CELL, 1)).hasMessage(message);
        assertThatThrownBy(() -> new CertificateTable("partA.summary", List.of(" "), List.of(PAID), ONE_CELL, 1)).hasMessage(message);
        assertThatThrownBy(() -> new CertificateTable("partA.summary", java.util.Arrays.asList((String) null), List.of(PAID), ONE_CELL, 1))
                .hasMessage(message);
        assertThatThrownBy(() -> new CertificateTable("partA.summary", List.of("Summary"), null, ONE_CELL, 1)).hasMessage(message);
        assertThatThrownBy(() -> new CertificateTable("partA.summary", List.of("Summary"), List.of(PAID), null, 1)).hasMessage(message);
        assertThatThrownBy(() -> new CertificateTable("partA.summary", List.of("Summary"), List.of(PAID), ONE_CELL, 0)).hasMessage(message);
    }

    @Test
    void newTable_whenItHasNoTitleAndNoTotal_isAOneRowTableFoundByItsHeadings() {
        // When — a row of Form 16's Part A boxes (p.28; W-126e3a, D-745 C1a)
        CertificateTable boxes = new CertificateTable("partA.certificate", List.of(), List.of(PAID), Optional.empty(), 28);

        // Then
        assertThat(boxes.title()).isEmpty();
        assertThat(boxes.total()).isEmpty();
    }

    @Test
    void newColumn_whenACaptionIsBlankNullOrPrintedTwice_refuses() {
        // When / Then
        String message = "Column cit's captions are printed words, each once: ";
        assertThatThrownBy(() -> new TableColumn("CIT (TDS)", "cit", CertificateValueKind.TEXT, Optional.empty(), null))
                .hasMessage(message + "null");
        assertThatThrownBy(() -> new TableColumn("CIT (TDS)", "cit", CertificateValueKind.TEXT, Optional.empty(), List.of(" ")))
                .hasMessage(message + "[ ]");
        assertThatThrownBy(() -> new TableColumn("CIT (TDS)", "cit", CertificateValueKind.TEXT, Optional.empty(),
                java.util.Arrays.asList((String) null))).hasMessage(message + "[null]");
        assertThatThrownBy(() -> new TableColumn("CIT (TDS)", "cit", CertificateValueKind.TEXT, Optional.empty(), List.of("City", "City")))
                .hasMessage(message + "[City, City]");
    }

    @Test
    void newTable_whenAGroupClosesTheTable_acceptsItAndRefusesItAlone() {
        // Given
        TableColumn code = new TableColumn("Code", "code", CertificateValueKind.TEXT, Optional.of("CIN"), List.of());
        TableColumn date = new TableColumn("Date", "date", CertificateValueKind.TEXT, Optional.of("CIN"), List.of());

        // When / Then — a group ending the table is checked at the end too
        assertThat(new CertificateTable("partA.summary", List.of("Summary"), List.of(PAID, code, date), ONE_CELL, 1).columns())
                .hasSize(3);
        assertThatThrownBy(() -> new CertificateTable("partA.summary", List.of("Summary"), List.of(PAID, code), ONE_CELL, 1))
                .hasMessage("partA.summary's group CIN is printed over one column; a heading is enough");
    }

    @Test
    void newField_whenAHeadingLineRepeats_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.group", Optional.empty(), "Group", CertificateValueKind.NONE,
                List.of(), true, 1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.group is a heading line; it has no columns and does not repeat");
    }

    @Test
    void newAgreement_whenAPartIsMissingTheSameOrNegative_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateAgreement(" ", "c.row21", com.thiru.wealthlens.taxengine.rules.money.Money.TEN, "G5.2"))
                .hasMessage("An agreement names two amounts, its tolerance and its basis");
        assertThatThrownBy(() -> new CertificateAgreement(null, "c.row21", com.thiru.wealthlens.taxengine.rules.money.Money.TEN, "G5.2"))
                .hasMessage("An agreement names two amounts, its tolerance and its basis");
        assertThatThrownBy(() -> new CertificateAgreement("b.total", null, com.thiru.wealthlens.taxengine.rules.money.Money.TEN, "G5.2"))
                .hasMessage("An agreement names two amounts, its tolerance and its basis");
        assertThatThrownBy(() -> new CertificateAgreement("b.total", " ", com.thiru.wealthlens.taxengine.rules.money.Money.TEN, "G5.2"))
                .hasMessage("An agreement names two amounts, its tolerance and its basis");
        assertThatThrownBy(() -> new CertificateAgreement("b.total", "c.row21", com.thiru.wealthlens.taxengine.rules.money.Money.TEN, null))
                .hasMessage("An agreement names two amounts, its tolerance and its basis");
        assertThatThrownBy(() -> new CertificateAgreement("b.total", "c.row21", null, "G5.2"))
                .hasMessage("An agreement names two amounts, its tolerance and its basis");
        assertThatThrownBy(() -> new CertificateAgreement("b.total", "c.row21", com.thiru.wealthlens.taxengine.rules.money.Money.TEN, " "))
                .hasMessage("An agreement names two amounts, its tolerance and its basis");
        assertThatThrownBy(() -> new CertificateAgreement("c.row21", "c.row21", com.thiru.wealthlens.taxengine.rules.money.Money.TEN, "G5.2"))
                .hasMessage("An agreement compares two amounts, not c.row21 with itself");
        assertThatThrownBy(() -> new CertificateAgreement("b.total", "c.row21", com.thiru.wealthlens.taxengine.rules.money.Money.of("-1"), "G5.2"))
                .hasMessage("An agreement's tolerance is not negative: -1.00");
    }

    @Test
    void newSpec_whenAnAgreementNamesSomethingNotAnAmountItPrints_refusesAndAcceptsALineAndATotal() {
        // Given — an AMOUNT line, a TEXT line, a two-column line, and a table whose Total is over an AMOUNT column
        CertificateField amount = new CertificateField("partA.tax", Optional.of("2"), "Tax", CertificateValueKind.AMOUNT, List.of(), false, 1, Optional.empty());
        CertificateField twoCells = new CertificateField("partA.ded", Optional.of("3"), "Deduction", CertificateValueKind.AMOUNT,
                List.of("Gross", "Net"), false, 1, Optional.empty());
        CertificateTable table = new CertificateTable("partA.summary", List.of("Summary"), List.of(PAID), ONE_CELL, 1);
        java.util.function.Function<String, CertificateSpec> naming = path -> new CertificateSpec(new CertificateCode("SYN_1"), "T",
                List.of(PART_A), List.of(NAME, amount, twoCells), List.of(table), new CertificateChecks(List.of(),
                List.of(new CertificateAgreement("partA.summary.total.paid", path, com.thiru.wealthlens.taxengine.rules.money.Money.TEN, "G5.2")), List.of()),
                SOURCE);

        // When / Then
        assertThat(naming.apply("partA.tax").checks().agreements()).hasSize(1);
        assertThatThrownBy(() -> naming.apply("partA.name")).hasMessage("SYN_1's agreement names partA.name, which is not an amount it prints");
        assertThatThrownBy(() -> naming.apply("partA.ded")).hasMessage("SYN_1's agreement names partA.ded, which is not an amount it prints");
        assertThatThrownBy(() -> naming.apply("partA.missing"))
                .hasMessage("SYN_1's agreement names partA.missing, which is not an amount it prints");
    }

    @Test
    void newSpec_whenAnAgreementNamesATotalTheTableDoesNotPrintAsAnAmount_refuses() {
        // Given — a one-row table with no Total, and a table whose Total cell is over a TEXT column
        CertificateField amount = new CertificateField("partA.tax", Optional.of("2"), "Tax", CertificateValueKind.AMOUNT, List.of(), false, 1, Optional.empty());
        TableColumn code = new TableColumn("Code", "code", CertificateValueKind.TEXT, Optional.empty(), List.of());
        CertificateTable oneRow = new CertificateTable("partA.boxes", List.of(), List.of(PAID), Optional.empty(), 1);
        CertificateTable textTotal = new CertificateTable("partA.codes", List.of("Codes"), List.of(code),
                Optional.of(new TableTotal("Total", List.of(List.of("code")))), 1);
        java.util.function.Function<String, CertificateSpec> naming = path -> new CertificateSpec(new CertificateCode("SYN_1"), "T",
                List.of(PART_A), List.of(NAME, amount), List.of(oneRow, textTotal), new CertificateChecks(List.of(),
                List.of(new CertificateAgreement("partA.tax", path, com.thiru.wealthlens.taxengine.rules.money.Money.TEN, "G5.2")), List.of()), SOURCE);

        // When / Then
        assertThatThrownBy(() -> naming.apply("partA.boxes.total.paid"))
                .hasMessage("SYN_1's agreement names partA.boxes.total.paid, which is not an amount it prints");
        assertThatThrownBy(() -> naming.apply("partA.codes.total.code"))
                .hasMessage("SYN_1's agreement names partA.codes.total.code, which is not an amount it prints");
    }

    @Test
    void newSpec_whenAPartNamesAFactTwice_refuses() {
        // Given — W-128c: a check finds a fact's line in its part, so the part names it once
        CertificateField first = new CertificateField("partA.tax", Optional.of("2"), "Tax", CertificateValueKind.AMOUNT, List.of(), false, 1,
                Optional.of(new CertificateFact("CESS")));
        CertificateField second = new CertificateField("partA.more", Optional.of("3"), "More", CertificateValueKind.AMOUNT, List.of(), false,
                1, Optional.of(new CertificateFact("CESS")));

        // When / Then
        assertThatThrownBy(() -> new CertificateSpec(new CertificateCode("SYN_1"), "T", List.of(PART_A), List.of(first, second), List.of(),
                NO_CHECKS, SOURCE)).hasMessage("SYN_1 names fact CESS twice in part partA");
        assertThatThrownBy(() -> new CertificateField("partA.tax", Optional.of("2"), "Tax", CertificateValueKind.AMOUNT, List.of(), false, 1,
                null)).hasMessage("partA.tax names its fact or none");
        assertThatThrownBy(() -> new CertificateFact("cess")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newComparison_whenItNamesBothANodeAndATotalOrNeither_refuses() {
        // When / Then — W-130a: a node's output or one of the computation's totals, exactly one
        Optional<ComparedTotal> bill = Optional.of(ComparedTotal.BILL);
        Optional<String> none = Optional.empty();
        assertThatThrownBy(() -> new CertificateComparison("c.row17", none, Optional.of(new com.thiru.wealthlens.taxengine.rules.id.NodeId("tax.cess")),
                bill, none, none, none)).hasMessage("c.row17 is compared with a node or a total, exactly one");
        assertThatThrownBy(() -> new CertificateComparison("c.row17", none, Optional.empty(), Optional.empty(), none, none, none))
                .hasMessage("c.row17 is compared with a node or a total, exactly one");
        assertThatThrownBy(() -> new CertificateComparison(" ", none, Optional.empty(), bill, none, none, none))
                .hasMessage("A comparison names its amount, a node or a total, and its reasons or none");
        assertThatThrownBy(() -> new CertificateComparison("c.row17", none, null, bill, none, none, none))
                .hasMessage("A comparison names its amount, a node or a total, and its reasons or none");
        assertThatThrownBy(() -> new CertificateComparison("c.row17", null, Optional.empty(), bill, none, none, none))
                .hasMessage("A comparison names its amount, a node or a total, and its reasons or none");
        assertThatThrownBy(() -> new CertificateComparison("c.row17", none, Optional.empty(), bill, none, null, none))
                .hasMessage("A comparison names its amount, a node or a total, and its reasons or none");
        assertThatThrownBy(() -> new CertificateComparison("c.row17", none, Optional.empty(), bill, none, none, null))
                .hasMessage("A comparison names its amount, a node or a total, and its reasons or none");
        // W-130b: no blank column or reason
        assertThatThrownBy(() -> new CertificateComparison("c.row17", none, Optional.empty(), bill, Optional.of(" "), none, none))
                .hasMessage("c.row17's comparison names no blank column or reason");
        assertThatThrownBy(() -> new CertificateComparison("c.row17", Optional.of(" "), Optional.empty(), bill, none, none, none))
                .hasMessage("c.row17's comparison names no blank column or reason");
        assertThatThrownBy(() -> new CertificateComparison("c.row17", none, Optional.empty(), bill, none, Optional.of(" "), none))
                .hasMessage("c.row17's comparison names no blank column or reason");
    }

    @Test
    void newSpec_whenAComparisonNamesAColumnTheLineDoesNotPrint_refuses() {
        // Given — W-130b: row 11's Deductible Amount is a column of a three-column AMOUNT line
        CertificateField row11 = new CertificateField("partA.row11", Optional.of("11"), "Aggregate", CertificateValueKind.AMOUNT,
                List.of("Gross Amount", "Deductible Amount"), false, 1, Optional.empty());
        java.util.function.Function<String, CertificateSpec> naming = column -> new CertificateSpec(new CertificateCode("SYN_1"), "T",
                List.of(PART_A), List.of(NAME, row11), List.of(), new CertificateChecks(List.of(), List.of(),
                        List.of(new CertificateComparison("partA.row11", Optional.of(column), Optional.empty(),
                                Optional.of(ComparedTotal.CHAPTER_VIII), Optional.empty(), Optional.empty(), Optional.empty()))), SOURCE);

        // When / Then
        assertThat(naming.apply("Deductible Amount").checks().comparisons()).hasSize(1);
        assertThatThrownBy(() -> naming.apply("Qualifying Amount"))
                .hasMessage("SYN_1's comparison names partA.row11 [Qualifying Amount], a cell it does not print");
    }

    @Test
    void newSpec_whenAComparisonNamesSomethingNotAnAmountItPrints_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateSpec(new CertificateCode("SYN_1"), "T", List.of(PART_A), List.of(NAME), List.of(),
                new CertificateChecks(List.of(), List.of(), List.of(new CertificateComparison("partA.name", Optional.empty(), Optional.empty(), Optional.of(ComparedTotal.BILL), Optional.empty(), Optional.empty(), Optional.empty()))),
                SOURCE)).hasMessage("SYN_1's comparison names partA.name, which is not an amount it prints");
    }
}
