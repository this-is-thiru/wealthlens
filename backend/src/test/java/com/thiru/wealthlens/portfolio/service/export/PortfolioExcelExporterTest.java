package com.thiru.wealthlens.portfolio.service.export;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.charges.AssetCharges;
import com.thiru.wealthlens.portfolio.dto.charges.ChargeNote;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.money.TMoney;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Both portfolio downloads, which now run on one framework.
 *
 * <p>An export is a published contract — a user builds formulas against these columns — so the
 * column list is stated here rather than derived. Changing it should be a deliberate edit to this
 * test, not a silent consequence of an edit somewhere else.
 */
@Tag("unit")
class PortfolioExcelExporterTest {

    private static final List<String> ASSET_COLUMNS = List.of(
            "EMAIL", "STOCK NAME", "STOCK CODE", "QUANTITY", "TOTAL QUANTITY", "PRICE",
            "TOTAL VALUE", "EXCHANGE NAME", "BROKER NAME", "ASSET TYPE", "MATURITY DATE",
            "BROKER CHARGES", "MISC CHARGES",
            "COMPUTED BUY CHARGES", "COMPUTED SELL CHARGES", "TOTAL COMPUTED CHARGES");

    @Test
    @DisplayName("assets: the columns are exactly the published set, in order")
    void assets_whenWritten_writesThePublishedColumns() throws IOException {
        Row headers = headerRow(PortfolioExcelExporter.assets(List.of(asset()), List.of(), false), 0);
        assertEquals(ASSET_COLUMNS, valuesOf(headers));
    }

    @Test
    @DisplayName("assets: every column carries a value")
    void assets_whenWritten_leavesNoBlankColumns() throws IOException {
        Sheet sheet = sheet(PortfolioExcelExporter.assets(List.of(asset()), List.of(), false), 0);
        Row row = sheet.getRow(1);

        assertEquals(sheet.getRow(0).getLastCellNum(), row.getLastCellNum());
        for (int i = 0; i < row.getLastCellNum(); i++) {
            assertNotEquals(CellType.BLANK, row.getCell(i).getCellType(),
                    "column " + i + " (" + ASSET_COLUMNS.get(i) + ") is blank");
        }
    }

    /**
     * The defect that motivated the consolidation. Asking for two columns used to write all sixteen
     * headers and put the two values under the first two of them, so the stock code appeared
     * beneath EMAIL and nothing about the file looked wrong.
     */
    @Test
    @DisplayName("selection: the header row describes the data beneath it")
    void assets_whenColumnsAreSelected_headersMatchTheValues() throws IOException {
        Sheet sheet = sheet(PortfolioExcelExporter.assets(
                List.of(asset()), List.of("stockCode", "price"), false), 0);

        assertEquals(List.of("STOCK CODE", "PRICE"), valuesOf(sheet.getRow(0)));
        assertEquals("INFY", sheet.getRow(1).getCell(0).getStringCellValue());
        assertMoney(1500.0, sheet.getRow(1).getCell(1).getNumericCellValue());
    }

    @Test
    @DisplayName("selection: columns come back in the order they were asked for")
    void assets_whenColumnsAreSelected_honoursTheRequestedOrder() throws IOException {
        Sheet sheet = sheet(PortfolioExcelExporter.assets(
                List.of(asset()), List.of("price", "stockCode"), false), 0);

        assertEquals(List.of("PRICE", "STOCK CODE"), valuesOf(sheet.getRow(0)));
    }

    /**
     * A misspelled column is the caller's error and is told so. It used to reach a null index and
     * fail as a 500 — and silently dropping it would return a file quietly missing a column that
     * was asked for.
     */
    @Test
    @DisplayName("selection: an unrecognised column is refused by name")
    void assets_whenColumnIsNotRecognised_refusesByName() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> PortfolioExcelExporter.assets(List.of(asset()), List.of("stockCode", "nope"), false));
        assertTrue(thrown.getMessage().contains("nope"), thrown.getMessage());
    }

    /**
     * The exported figure has to match the figure on screen, and the application rounds through
     * TMoney. A second rounding implementation is how a spreadsheet and a screen come to disagree.
     */
    @Test
    @DisplayName("assets: money rounds the way the rest of the application rounds it")
    void assets_whenAmountIsHalfWay_roundsAsTMoneyDoes() throws IOException {
        AssetResponse asset = asset();
        asset.setBrokerCharges(-2.505);
        Row row = sheet(PortfolioExcelExporter.assets(List.of(asset), List.of(), false), 0).getRow(1);

        assertMoney(TMoney.scale(-2.505), row.getCell(11).getNumericCellValue());
    }

    @Test
    @DisplayName("assets: a null quantity does not blow the export up")
    void assets_whenQuantityIsNull_writesWithoutFailing() {
        AssetResponse asset = asset();
        asset.setQuantity(null);
        asset.setTotalQuantity(null);

        assertDoesNotThrow(() -> PortfolioExcelExporter.assets(List.of(asset), List.of(), false));
    }

    @Test
    @DisplayName("assets: the computed charges reach the spreadsheet")
    void assets_whenChargesWereComputed_writesThem() throws IOException {
        AssetResponse asset = asset();
        asset.setCharges(charges(20.00, 30.00));

        Row row = sheet(PortfolioExcelExporter.assets(List.of(asset), List.of(), false), 0).getRow(1);

        assertMoney(23.60, row.getCell(11).getNumericCellValue());
        assertMoney(20.00, row.getCell(13).getNumericCellValue());
        assertMoney(30.00, row.getCell(14).getNumericCellValue());
        assertMoney(50.00, row.getCell(15).getNumericCellValue());
    }

    @Test
    @DisplayName("assets: a holding the engine never priced exports zeroes, not blanks")
    void assets_whenNeverPriced_writesZeroes() throws IOException {
        Row row = sheet(PortfolioExcelExporter.assets(List.of(asset()), List.of(), false), 0).getRow(1);

        assertMoney(0.0, row.getCell(13).getNumericCellValue());
        assertMoney(0.0, row.getCell(15).getNumericCellValue());
    }

    /** The second sheet declared BROKER NAME twice and wrote the transaction date into it. */
    @Test
    @DisplayName("breakdown sheet: the header row describes the data beneath it")
    void assets_whenBreakdownRequested_headersMatchTheValues() throws IOException {
        Sheet sheet = sheet(PortfolioExcelExporter.assets(List.of(asset()), List.of(), true), 1);

        assertEquals(List.of("STOCK CODE", "BROKER NAME", "TRANSACTION DATE", "QUANTITY"),
                valuesOf(sheet.getRow(0)));
        assertEquals("2025-04-01", sheet.getRow(1).getCell(2).getStringCellValue());
    }

    @Test
    @DisplayName("breakdown sheet: a holding with no breakdown is skipped, not fatal")
    void assets_whenTransactionQuantitiesAreNull_doesNotFail() {
        AssetResponse asset = asset();
        asset.setTransactionQuantities(null);

        assertDoesNotThrow(() -> PortfolioExcelExporter.assets(List.of(asset), List.of(), true));
    }

    /**
     * Styles are workbook-scoped and XLSX caps the format table at 64k, so one per cell fails to
     * write once an export is large enough. Both superseded mechanisms did exactly that.
     */
    @Test
    @DisplayName("styles are created once for the workbook, not once per cell")
    void assets_whenManyRows_doesNotCreateAStylePerCell() throws IOException {
        List<AssetResponse> assets = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            assets.add(asset());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                PortfolioExcelExporter.assets(assets, List.of(), true))) {
            assertTrue(workbook.getNumCellStyles() < 20,
                    "workbook holds " + workbook.getNumCellStyles() + " cell styles for 200 rows");
        }
    }

    @Test
    @DisplayName("transactions: exports the trade columns")
    void transactions_whenWritten_writesTheTradeColumns() throws IOException {
        Sheet sheet = sheet(PortfolioExcelExporter.transactions(List.of(transaction()), List.of()), 0);

        assertEquals("EMAIL", sheet.getRow(0).getCell(0).getStringCellValue());
        assertEquals("INFY", sheet.getRow(1).getCell(1).getStringCellValue());
        assertEquals("BUY", sheet.getRow(1).getCell(6).getStringCellValue());
    }

    /** The template must carry every column the upload parser reads, or a round trip loses data. */
    @Test
    @DisplayName("template: carries the columns the upload parser reads back")
    void transactionTemplate_whenWritten_carriesTheParsedColumns() throws IOException {
        Sheet sheet = sheet(PortfolioExcelExporter.transactionTemplate(List.of(transaction()), List.of()), 0);

        assertEquals(List.of("EMAIL", "STOCK CODE", "STOCK NAME", "ASSET TYPE", "EXCHANGE NAME",
                        "BROKER NAME", "TRANSACTION TYPE", "QUANTITY", "PRICE", "TRANSACTION DATE",
                        "MATURITY DATE", "BROKER CHARGES", "MISC CHARGES", "COMMENTS"),
                valuesOf(sheet.getRow(0)));
    }

    /** Two columns sharing a heading make an export ambiguous to read. */
    @Test
    @DisplayName("no export declares the same heading twice")
    void everyExport_whenWritten_hasDistinctHeadings() throws IOException {
        for (Sheet sheet : List.of(
                sheet(PortfolioExcelExporter.assets(List.of(asset()), List.of(), false), 0),
                sheet(PortfolioExcelExporter.assets(List.of(asset()), List.of(), true), 1),
                sheet(PortfolioExcelExporter.transactions(List.of(transaction()), List.of()), 0),
                sheet(PortfolioExcelExporter.transactionTemplate(List.of(transaction()), List.of()), 0))) {
            List<String> headers = valuesOf(sheet.getRow(0));
            assertEquals(headers.size(), headers.stream().distinct().count(),
                    sheet.getSheetName() + " repeats a heading: " + headers);
        }
    }

    private static List<String> valuesOf(Row row) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            values.add(row.getCell(i).getStringCellValue());
        }
        return values;
    }

    private static Row headerRow(ByteArrayInputStream workbook, int index) throws IOException {
        return sheet(workbook, index).getRow(0);
    }

    private static Sheet sheet(ByteArrayInputStream stream, int index) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
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

    private static TransactionEntity transaction() {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setEmail("test@example.com");
        transaction.setStockCode("INFY");
        transaction.setStockName("Infosys");
        transaction.setExchangeName("NSE");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setTransactionDate(LocalDate.of(2025, 4, 1));
        transaction.setQuantity(10D);
        transaction.setPrice(1500D);
        transaction.setComment("comments");
        return transaction;
    }

    private static AssetCharges charges(double buy, double sell) {
        ChargeNote buyNote = new ChargeNote();
        buyNote.setTotal(buy);
        ChargeNote sellNote = new ChargeNote();
        sellNote.setTotal(sell);
        AssetCharges charges = new AssetCharges();
        charges.setBuy(buyNote);
        charges.setSell(sellNote);
        charges.setTotal(buy + sell);
        return charges;
    }
}
