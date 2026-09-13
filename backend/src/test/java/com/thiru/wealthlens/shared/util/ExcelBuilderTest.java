package com.thiru.wealthlens.shared.util;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.util.parser.ExcelBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The portfolio export is a published contract: a user builds formulas against these columns, so a
 * shifted or stray one is a defect in someone else's spreadsheet.
 */
@Tag("unit")
class ExcelBuilderTest {

    /**
     * The exported columns, in order. Stated here rather than derived, because the export is a
     * published contract: if this list changes, someone's spreadsheet breaks, and that should be a
     * deliberate edit to a test rather than a silent consequence of an edit elsewhere.
     */
    private static final List<String> PORTFOLIO_COLUMNS = List.of(
            "EMAIL", "STOCK NAME", "STOCK CODE", "QUANTITY", "TOTAL QUANTITY", "PRICE",
            "TOTAL VALUE", "EXCHANGE NAME", "BROKER NAME", "ASSET TYPE", "MATURITY DATE",
            "BROKER CHARGES", "MISC CHARGES");

    @Test
    @DisplayName("portfolio export: the columns are exactly the published set, in order")
    void downloadAssets_whenWritten_writesThePublishedColumns() throws IOException {
        // Given
        List<AssetResponse> assets = List.of(asset(10, 1500, 23.60, 1.50));

        // When
        Row headers = headerRow(ExcelBuilder.downloadAssets(assets, false));

        // Then
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < headers.getLastCellNum(); i++) {
            actual.add(headers.getCell(i).getStringCellValue());
        }
        assertEquals(PORTFOLIO_COLUMNS, actual);
    }

    /** A cell with a header above it and nothing in it is a column that should not be there. */
    @Test
    @DisplayName("portfolio export: every column carries a value")
    void downloadAssets_whenWritten_leavesNoBlankColumns() throws IOException {
        // Given
        List<AssetResponse> assets = List.of(asset(10, 1500, 23.60, 1.50));

        // When
        Sheet sheet = sheet(ExcelBuilder.downloadAssets(assets, false), 0);
        Row row = sheet.getRow(1);

        // Then
        assertEquals(sheet.getRow(0).getLastCellNum(), row.getLastCellNum(),
                "the data row and the header row must have the same width");
        for (int i = 0; i < row.getLastCellNum(); i++) {
            assertNotEquals(CellType.BLANK, row.getCell(i).getCellType(),
                    "column " + i + " (" + PORTFOLIO_COLUMNS.get(i) + ") is blank");
        }
    }

    /**
     * The exported figure has to match the figure the application reports, and the application
     * rounds through {@code TMoney}. A second rounding implementation is how the spreadsheet and
     * the screen come to disagree by a paisa.
     */
    @Test
    @DisplayName("portfolio export: money is rounded the way the rest of the application rounds it")
    void downloadAssets_whenAmountIsHalfWay_roundsAsTMoneyDoes() throws IOException {
        // Given: a charge that the two rounding implementations disagree about
        List<AssetResponse> assets = List.of(asset(10, 1500, -2.505, 0));

        // When
        Row row = sheet(ExcelBuilder.downloadAssets(assets, false), 0).getRow(1);

        // Then
        assertMoney(TMoneyRef.scale(-2.505), row.getCell(11).getNumericCellValue());
    }

    /** Quantity is a boxed Double on the response, and an unsold-out holding can arrive null. */
    @Test
    @DisplayName("portfolio export: a null quantity does not blow the export up")
    void downloadAssets_whenQuantityIsNull_writesWithoutFailing() {
        // Given
        AssetResponse asset = asset(10, 1500, 0, 0);
        asset.setQuantity(null);
        asset.setTotalQuantity(null);

        // When / Then
        assertDoesNotThrow(() -> ExcelBuilder.downloadAssets(List.of(asset), false));
    }

    private static Row headerRow(ByteArrayInputStream workbook) throws IOException {
        return sheet(workbook, 0).getRow(0);
    }

    private static Sheet sheet(ByteArrayInputStream stream, int index) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
            return workbook.getSheetAt(index);
        }
    }

    private static AssetResponse asset(double quantity, double price, double brokerCharges, double miscCharges) {
        AssetResponse asset = new AssetResponse();
        asset.setEmail("test@example.com");
        asset.setStockCode("INFY");
        asset.setStockName("Infosys");
        asset.setExchangeName("NSE");
        asset.setBrokerName(BrokerName.ZERODHA);
        asset.setAssetType(AssetType.EQUITY);
        asset.setMaturityDate(LocalDate.of(2030, 3, 31));
        asset.setQuantity(quantity);
        asset.setTotalQuantity(quantity);
        asset.setPrice(price);
        asset.setTotalValue(price * quantity);
        asset.setBrokerCharges(brokerCharges);
        asset.setMiscCharges(miscCharges);
        asset.setTransactionQuantities(new LinkedHashMap<>());
        asset.setBuyTransactionIds(new ArrayList<>());
        asset.setSellTransactionIds(new ArrayList<>());
        return asset;
    }

    /** Indirection so the expectation states the rule rather than a literal. */
    private static final class TMoneyRef {
        static double scale(double amount) {
            return com.thiru.wealthlens.shared.util.money.TMoney.scale(amount);
        }
    }
}
