package com.thiru.wealthlens.portfolio.service.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.shared.dto.InputRecord;
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
}
