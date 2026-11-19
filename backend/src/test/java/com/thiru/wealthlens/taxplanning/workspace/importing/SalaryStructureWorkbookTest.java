package com.thiru.wealthlens.taxplanning.workspace.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The child's read of a salary workbook, in process (W-133a C2a). */
class SalaryStructureWorkbookTest {

    @Test
    void read_whenTheHeaderNamesItsColumnsInAnyCaseAndOrder_readsEachRowAsText() throws IOException {
        // Given — the columns out of order and in another case; numbers, a text amount and a blank monthly cell
        byte[] workbook = Workbooks.sheet(new Object[] {"monthly amount", " Component ", "ANNUAL AMOUNT"},
                new Object[] {50000, "Basic salary", 600000},
                new Object[] {null, "Special allowance", "3,00,000"},
                new Object[] {12500.5, "HRA", null});

        // When
        WorkbookRows read = SalaryStructureWorkbook.read(workbook);

        // Then — row numbers as the spreadsheet shows them, from 1
        assertThat(read.outcome()).isEqualTo(WorkbookRows.Outcome.READ);
        assertThat(read.rows()).containsExactly(new WorkbookRows.Row(2, "Basic salary", "600000", "50000"),
                new WorkbookRows.Row(3, "Special allowance", "3,00,000", ""), new WorkbookRows.Row(4, "HRA", "", "12500.5"));
    }

    @Test
    void read_whenARowIsBlank_skipsItAndKeepsTheRowNumbersOfTheRest() throws IOException {
        // Given
        byte[] workbook = Workbooks.sheet(new Object[] {"Component", "Annual Amount"}, new Object[] {"Bonus", 10000}, new Object[] {},
                new Object[] {"Commission", 2000});

        // When / Then
        assertThat(SalaryStructureWorkbook.read(workbook).rows()).extracting(WorkbookRows.Row::number).containsExactly(2, 4);
    }

    @Test
    void read_whenNoComponentColumn_isUnreadableNamingIt() throws IOException {
        // When
        WorkbookRows read = SalaryStructureWorkbook.read(Workbooks.sheet(new Object[] {"Head", "Annual Amount"}, new Object[] {"Basic", 1}));

        // Then
        assertThat(read.outcome()).isEqualTo(WorkbookRows.Outcome.UNREADABLE);
        assertThat(read.reason()).isEqualTo("The first row names no \"Component\" column");
    }

    @Test
    void read_whenNeitherAmountColumn_isUnreadableNamingBoth() throws IOException {
        // When
        WorkbookRows read = SalaryStructureWorkbook.read(Workbooks.sheet(new Object[] {"Component", "Amount"}, new Object[] {"Basic", 1}));

        // Then
        assertThat(read.reason()).isEqualTo("The first row names neither \"Annual Amount\" nor \"Monthly Amount\"");
    }

    @Test
    void read_whenMoreThanTwoHundredRows_isUnreadable() throws IOException {
        // Given — 200 rows are read; the 201st is one too many
        Object[][] rows = new Object[202][];
        rows[0] = new Object[] {"Component", "Annual Amount"};
        for (int at = 1; at < rows.length; at++) {
            rows[at] = new Object[] {"Bonus", at};
        }
        Object[][] twoHundred = java.util.Arrays.copyOf(rows, 201);

        // When / Then
        assertThat(SalaryStructureWorkbook.read(Workbooks.sheet(twoHundred)).rows()).hasSize(200);
        assertThat(SalaryStructureWorkbook.read(Workbooks.sheet(rows)).reason()).isEqualTo("More than 200 rows: a salary structure is tens of rows");
    }

    @Test
    void read_whenTheBytesAreNoWorkbook_throwsSoTheChildExitsWithAnError() {
        // When / Then — the child's non-zero exit is the application's "could not be read"
        assertThatThrownBy(() -> SalaryStructureWorkbook.read("a csv, not a workbook".getBytes(StandardCharsets.US_ASCII))).isNotNull();
    }
}
