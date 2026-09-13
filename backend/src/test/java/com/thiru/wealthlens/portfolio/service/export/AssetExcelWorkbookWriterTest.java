package com.thiru.wealthlens.portfolio.service.export;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.service.export.writer.AssetExcelWorkbookWriter;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The selective export reached by {@code POST /portfolio/user/{email}/stocks/download}.
 *
 * <p>{@code selectedColumns} arrives from the request body, so every one of these is a caller-
 * reachable path. None of it had a test, which is how a header row that disagreed with its own data
 * survived.
 */
@Tag("unit")
class AssetExcelWorkbookWriterTest {

    /**
     * The defect this class exists to prevent. Asking for two columns used to write all thirteen
     * headers and then put the two values under the first two of them — so the stock code appeared
     * beneath EMAIL, and nothing about the file looked wrong.
     */
    @Test
    @DisplayName("selected columns: the header row describes the data beneath it")
    void process_whenColumnsAreSelected_headersMatchTheValues() throws IOException {
        // Given
        var writer = new AssetExcelWorkbookWriter("ASSETS", List.of("stockCode", "price"));

        // When
        Sheet sheet = firstSheet(writer.process(List.of(asset())));

        // Then
        assertEquals(List.of("STOCK CODE", "PRICE"), headersOf(sheet));
        assertEquals("INFY", sheet.getRow(1).getCell(0).getStringCellValue());
        assertEquals(1500.0, sheet.getRow(1).getCell(1).getNumericCellValue());
    }

    /** An empty selection means everything, which is the existing contract. */
    @Test
    @DisplayName("no selection: every declared column is exported")
    void process_whenNoColumnsSelected_exportsAllOfThem() throws IOException {
        // Given
        var writer = new AssetExcelWorkbookWriter("ASSETS", List.of());

        // When
        Sheet sheet = firstSheet(writer.process(List.of(asset())));

        // Then
        assertEquals(13, headersOf(sheet).size());
        assertEquals("test@example.com", sheet.getRow(1).getCell(0).getStringCellValue());
    }

    /**
     * A misspelled column is the caller's error and is told so. It used to reach a null index and
     * fail as a 500 — and silently dropping it instead would export a file quietly missing a column
     * the caller asked for.
     */
    @Test
    @DisplayName("unknown column: refused by name rather than failing as a server error")
    void process_whenColumnIsNotRecognised_refusesByName() {
        // When / Then
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> new AssetExcelWorkbookWriter("ASSETS", List.of("stockCode", "notAColumn")));
        assertTrue(thrown.getMessage().contains("notAColumn"), thrown.getMessage());
    }

    /** The second sheet declared BROKER NAME twice and wrote a date into the second one. */
    @Test
    @DisplayName("transactions sheet: the header row describes the data beneath it")
    void process_whenWritingTheTransactionsSheet_headersMatchTheValues() throws IOException {
        // Given
        var writer = new AssetExcelWorkbookWriter("ASSETS", List.of());

        // When
        Sheet sheet = sheetAt(writer.process(List.of(asset())), 1);

        // Then
        assertEquals(List.of("STOCK CODE", "BROKER NAME", "TRANSACTION DATE", "QUANTITY"), headersOf(sheet));
        assertEquals("2025-04-01", sheet.getRow(1).getCell(2).getStringCellValue());
    }

    /** A holding with no per-date breakdown must not fail the whole export. */
    @Test
    @DisplayName("transactions sheet: a holding with no breakdown is skipped, not fatal")
    void process_whenTransactionQuantitiesAreNull_doesNotFail() {
        // Given
        AssetResponse asset = asset();
        asset.setTransactionQuantities(null);

        // When / Then
        assertDoesNotThrow(() -> new AssetExcelWorkbookWriter("ASSETS", List.of()).process(List.of(asset)));
    }

    /**
     * Styles are workbook-scoped and XLSX caps the format table at 64k, so one per cell fails to
     * write once an export is large enough. This is the same fault already fixed in ExcelBuilder.
     */
    @Test
    @DisplayName("styles are created once for the workbook, not once per cell")
    void process_whenManyRows_doesNotCreateAStylePerCell() throws IOException {
        // Given
        List<AssetResponse> assets = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            assets.add(asset());
        }

        // When
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new AssetExcelWorkbookWriter("ASSETS", List.of()).process(assets).getInputStream())) {
            // Then
            assertTrue(workbook.getNumCellStyles() < 20,
                    "workbook holds " + workbook.getNumCellStyles() + " cell styles for 200 rows");
        }
    }

    private static List<String> headersOf(Sheet sheet) {
        Row headers = sheet.getRow(0);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < headers.getLastCellNum(); i++) {
            names.add(headers.getCell(i).getStringCellValue());
        }
        return names;
    }

    private static Sheet firstSheet(org.springframework.core.io.InputStreamResource resource) throws IOException {
        return sheetAt(resource, 0);
    }

    private static Sheet sheetAt(org.springframework.core.io.InputStreamResource resource, int index)
            throws IOException {
        try (InputStream in = resource.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            return workbook.getSheetAt(index);
        }
    }

    private static AssetResponse asset() {
        AssetResponse asset = new AssetResponse();
        asset.setEmail("test@example.com");
        asset.setStockCode("INFY");
        asset.setStockName("Infosys");
        asset.setExchangeName("NSE");
        asset.setBrokerName(BrokerName.ZERODHA);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(10D);
        asset.setTotalQuantity(10D);
        asset.setPrice(1500D);
        asset.setTotalValue(15000D);
        asset.setMaturityDate(LocalDate.of(2030, 3, 31));
        asset.setBrokerCharges(23.60);
        asset.setMiscCharges(1.50);
        Map<String, Double> quantities = new LinkedHashMap<>();
        quantities.put("2025-04-01", 10D);
        asset.setTransactionQuantities(quantities);
        return asset;
    }
}
