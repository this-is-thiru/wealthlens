package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** The workbook holds what the view holds, a sheet for the lots and one for the totals (W-262). */
class HeldAssetsWorkbookTest {

    private static List<String> headings(Sheet sheet) {
        Row header = sheet.getRow(0);
        return IntStream.range(0, header.getLastCellNum()).mapToObj(index -> header.getCell(index).getStringCellValue())
                .toList();
    }

    @Test
    void of_whenTheViewHasALotWithATermAndOneWithout_writesBothSheets() throws IOException {
        // Given
        HeldAssetsView view = new HeldAssetsView(LocalDate.of(2026, 11, 6), List.of(
                new HeldAssetsView.Lot("HELD", "ZERODHA", "Asha", 10, LocalDate.of(2025, 6, 1), "LONG_TERM", "CG-TERM-LISTED-SECURITY",
                        12, LocalDate.of(2026, 6, 2), null),
                new HeldAssetsView.Lot("HELD", null, null, 2, LocalDate.of(2025, 1, 1), null, null, null, null, "No row covers this lot")),
                List.of(new HeldAssetsView.StockTotal("HELD", 10, 0, 2)));

        // When
        try (XSSFWorkbook workbook = new XSSFWorkbook(HeldAssetsWorkbook.of(view))) {
            // Then
            Sheet lots = workbook.getSheet("LOTS");
            assertThat(headings(lots)).containsExactly("STOCK CODE", "BROKER", "ACCOUNT HOLDER", "QUANTITY", "ACQUIRED ON", "TERM",
                    "RULE ROW", "HOLDING PERIOD (MONTHS)", "LONG TERM FROM", "NOTE");
            assertThat(lots.getLastRowNum()).isEqualTo(2);
            Row first = lots.getRow(1);
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo("HELD");
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("ZERODHA");
            assertThat(first.getCell(3).getNumericCellValue()).isEqualTo(10.0);
            assertThat(first.getCell(4).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2025, 6, 1));
            assertThat(first.getCell(5).getStringCellValue()).isEqualTo("LONG_TERM");
            assertThat(first.getCell(7).getNumericCellValue()).isEqualTo(12.0);
            assertThat(first.getCell(8).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 6, 2));
            assertThat(lots.getRow(2).getCell(9).getStringCellValue()).isEqualTo("No row covers this lot");

            Sheet totals = workbook.getSheet("TOTALS");
            assertThat(headings(totals)).containsExactly("STOCK CODE", "LONG TERM QUANTITY", "SHORT TERM QUANTITY",
                    "QUANTITY WITHOUT TERM");
            Row total = totals.getRow(1);
            assertThat(total.getCell(1).getNumericCellValue()).isEqualTo(10.0);
            assertThat(total.getCell(2).getNumericCellValue()).isEqualTo(0.0);
            assertThat(total.getCell(3).getNumericCellValue()).isEqualTo(2.0);
        }
    }
}
