package com.thiru.wealthlens.portfolio.service.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.dto.InputRecord;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.enums.ExcelDataType;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.parser.CellDetail;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AssetRequestParserTest {

    private final AssetRequestParser parser = new AssetRequestParser("Q1");

    private static InputRecord row(int recordNumber, String assetType, CellDetail maturityDate) {
        Map<String, CellDetail> record = new HashMap<>();
        record.put(ExcelHeaders.STOCK_CODE, CellDetail.of(ExcelDataType.STRING, "SGBFEB32IV"));
        record.put(ExcelHeaders.STOCK_NAME, CellDetail.of(ExcelDataType.STRING, "SGB 2023-24 Series IV"));
        record.put(ExcelHeaders.EXCHANGE_NAME, CellDetail.of(ExcelDataType.STRING, "NSE"));
        record.put(ExcelHeaders.BROKER_NAME, CellDetail.of(ExcelDataType.STRING, "ZERODHA"));
        record.put(ExcelHeaders.ASSET_TYPE, CellDetail.of(ExcelDataType.STRING, assetType));
        record.put(ExcelHeaders.MATURITY_DATE, maturityDate);
        record.put(ExcelHeaders.PRICE, CellDetail.of(ExcelDataType.DOUBLE, 6263.0));
        record.put(ExcelHeaders.QUANTITY, CellDetail.of(ExcelDataType.DOUBLE, 2.0));
        record.put(ExcelHeaders.TRANSACTION_TYPE, CellDetail.of(ExcelDataType.STRING, "BUY"));
        record.put(ExcelHeaders.TRANSACTION_DATE, CellDetail.of(ExcelDataType.LOCAL_DATE, LocalDate.of(2025, 6, 10)));
        record.put(ExcelHeaders.BROKER_CHARGES, CellDetail.of(ExcelDataType.DOUBLE, 0.0));
        record.put(ExcelHeaders.MISC_CHARGES, CellDetail.of(ExcelDataType.DOUBLE, 0.0));

        InputRecord inputRecord = new InputRecord();
        inputRecord.setRecord(record);
        inputRecord.setRecordNumber(recordNumber);
        return inputRecord;
    }

    private static CellDetail date(LocalDate value) {
        return CellDetail.of(ExcelDataType.LOCAL_DATE, value);
    }

    @Test
    void toRequest_whenGoldBondHasNoMaturityDate_refusesNamingTheRow() {
        // Given
        InputRecord inputRecord = row(3, "GOLD_BOND", CellDetail.def());

        // When
        BadRequestException refusal = assertThrows(BadRequestException.class, () -> parser.toRequest(inputRecord));

        // Then
        assertEquals("Row 3: a GOLD_BOND row needs its MATURITY DATE; it is not assumed", refusal.getMessage());
    }

    @Test
    void toRequest_whenTheRowIsALandOrBuilding_refusesNamingTheRow() {
        // Given — a plot on row 4 of an upload
        InputRecord inputRecord = row(4, "LAND_OR_BUILDING", CellDetail.def());

        // When
        BadRequestException refusal = assertThrows(BadRequestException.class, () -> parser.toRequest(inputRecord));

        // Then
        assertEquals("Row 4: " + AssetType.NOT_A_BROKER_TRADE, refusal.getMessage());
    }

    @Test
    void toRequest_whenGoldBondHasMaturityDate_keepsIt() {
        // Given
        InputRecord inputRecord = row(1, "GOLD_BOND", date(LocalDate.of(2032, 2, 20)));

        // When
        AssetRequest request = parser.toRequest(inputRecord);

        // Then
        assertEquals(LocalDate.of(2032, 2, 20), request.getMaturityDate());
    }

    @Test
    void toRequest_whenBondHasMaturityDate_keepsIt() {
        // Given
        InputRecord inputRecord = row(1, "BOND", date(LocalDate.of(2030, 3, 31)));

        // When
        AssetRequest request = parser.toRequest(inputRecord);

        // Then
        assertEquals(LocalDate.of(2030, 3, 31), request.getMaturityDate());
    }

    @Test
    void toRequest_whenEquityHasNoMaturityDate_leavesItAbsent() {
        // Given
        InputRecord inputRecord = row(1, "EQUITY", CellDetail.def());

        // When
        AssetRequest request = parser.toRequest(inputRecord);

        // Then
        assertNull(request.getMaturityDate());
    }
    @Test
    void toRequest_whenTheRowNamesAnAccountHolder_keepsIt() {
        // Given -- a row with an ACCOUNT HOLDER cell (W-285, D-559)
        InputRecord record = row(2, "EQUITY", CellDetail.def());
        record.getRecord().put(ExcelHeaders.ACCOUNT_HOLDER, CellDetail.of(ExcelDataType.STRING, "Lakshmi"));

        // When
        AssetRequest request = parser.toRequest(record);

        // Then
        assertEquals("Lakshmi", request.getAccountHolder());
    }

    @Test
    void toRequest_whenTheRowHasNoAccountHolder_leavesItAbsentAsJsonDoes() {
        // Given -- a workbook made before the column existed
        InputRecord record = row(2, "EQUITY", CellDetail.def());

        // When
        AssetRequest request = parser.toRequest(record);

        // Then -- no holder, as a JSON trade without one
        assertNull(request.getAccountHolder());
    }

    @Test
    void toRequest_whenTheRowNamesItsSegmentAndAccountType_keepsBoth() {
        // Given -- an intraday row for an outsourced account (W-301, D-606)
        InputRecord record = row(2, "EQUITY", CellDetail.def());
        record.getRecord().put(ExcelHeaders.SEGMENT, CellDetail.of(ExcelDataType.STRING, "INTRADAY"));
        record.getRecord().put(ExcelHeaders.ACCOUNT_TYPE, CellDetail.of(ExcelDataType.STRING, "OUTSOURCED"));

        // When
        AssetRequest request = parser.toRequest(record);

        // Then
        assertEquals(TradeSegment.INTRADAY, request.getSegment());
        assertEquals(AccountType.OUTSOURCED, request.getAccountType());
    }

    @Test
    void toRequest_whenTheRowHasNoSegmentOrAccountType_takesTheDefaultsJsonDoes() {
        // Given -- a workbook made before the columns existed
        InputRecord record = row(2, "EQUITY", CellDetail.def());

        // When
        AssetRequest request = parser.toRequest(record);

        // Then -- delivery, on the user's own account, as a JSON trade without them
        assertEquals(TradeSegment.DELIVERY, request.getSegment());
        assertEquals(AccountType.SELF, request.getAccountType());
    }

    @Test
    void toRequest_whenTheSegmentIsNotOne_refusesNamingTheRowAndTheWords() {
        // Given
        InputRecord record = row(3, "EQUITY", CellDetail.def());
        record.getRecord().put(ExcelHeaders.SEGMENT, CellDetail.of(ExcelDataType.STRING, "intra"));

        // When
        BadRequestException refusal = assertThrows(BadRequestException.class, () -> parser.toRequest(record));

        // Then
        assertEquals("Row 3, SEGMENT: \"intra\" is not one of [DELIVERY, INTRADAY, FUTURES, OPTIONS, NA]",
                refusal.getMessage());
    }

    @Test
    void toRequest_whenTheBrokerIsNotOne_refusesNamingTheRowAndTheWords() {
        // Given -- the broker in the wrong case (W-302, D-608)
        InputRecord record = row(3, "EQUITY", CellDetail.def());
        record.getRecord().put(ExcelHeaders.BROKER_NAME, CellDetail.of(ExcelDataType.STRING, "Zerodha"));

        // When
        BadRequestException refusal = assertThrows(BadRequestException.class, () -> parser.toRequest(record));

        // Then
        assertEquals("Row 3, BROKER NAME: \"Zerodha\" is not one of [UPSTOX, FYERS, ZERODHA]", refusal.getMessage());
    }

    @Test
    void toRequest_whenTheAssetTypeIsNotOne_refusesNamingTheRowAndTheWords() {
        // Given
        InputRecord record = row(3, "STOCK", CellDetail.def());

        // When
        BadRequestException refusal = assertThrows(BadRequestException.class, () -> parser.toRequest(record));

        // Then -- every constant is listed; a LAND_OR_BUILDING row is refused on its own (D-609 C2a)
        assertEquals("Row 3, ASSET TYPE: \"STOCK\" is not one of "
                + "[EQUITY, MUTUAL_FUND, BOND, GOLD_BOND, FD, INSURANCE, LAND_OR_BUILDING]", refusal.getMessage());
    }

    @Test
    void toRequest_whenTheTransactionTypeIsNotOne_refusesNamingTheRowAndTheWords() {
        // Given
        InputRecord record = row(3, "EQUITY", CellDetail.def());
        record.getRecord().put(ExcelHeaders.TRANSACTION_TYPE, CellDetail.of(ExcelDataType.STRING, "buy"));

        // When
        BadRequestException refusal = assertThrows(BadRequestException.class, () -> parser.toRequest(record));

        // Then
        assertEquals("Row 3, TRANSACTION TYPE: \"buy\" is not one of [BUY, SELL]", refusal.getMessage());
    }

    @Test
    void toRequest_whenTheBrokerIsBlank_refusesRatherThanStoringNone() {
        // Given -- a required column has no JSON default to fall back on (D-609 C1a)
        InputRecord record = row(3, "EQUITY", CellDetail.def());
        record.getRecord().put(ExcelHeaders.BROKER_NAME, CellDetail.def());

        // When
        BadRequestException refusal = assertThrows(BadRequestException.class, () -> parser.toRequest(record));

        // Then
        assertEquals("Row 3, BROKER NAME: blank; one of [UPSTOX, FYERS, ZERODHA] is needed", refusal.getMessage());
    }

    @Test
    void toRequest_whenTheWorkbookHasNoTransactionTypeColumn_refusesRatherThanStoringNone() {
        // Given
        InputRecord record = row(3, "EQUITY", CellDetail.def());
        record.getRecord().remove(ExcelHeaders.TRANSACTION_TYPE);

        // When
        BadRequestException refusal = assertThrows(BadRequestException.class, () -> parser.toRequest(record));

        // Then
        assertEquals("Row 3, TRANSACTION TYPE: blank; one of [BUY, SELL] is needed", refusal.getMessage());
    }
}
