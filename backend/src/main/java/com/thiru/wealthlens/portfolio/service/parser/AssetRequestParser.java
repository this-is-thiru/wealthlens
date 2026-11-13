package com.thiru.wealthlens.portfolio.service.parser;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.service.parser.model.AbstractRequestParser;
import com.thiru.wealthlens.portfolio.service.parser.model.AssetRequestParserImpl;
import com.thiru.wealthlens.portfolio.service.parser.model.ExcelParser;
import com.thiru.wealthlens.shared.dto.InputRecord;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.enums.ExcelDataType;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TOptional;
import com.thiru.wealthlens.shared.util.parser.CellDetail;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AssetRequestParser extends AbstractRequestParser<AssetRequest> {

    private final String quarter;
    public AssetRequestParser(String quarter) {
        this.quarter = quarter;
    }

    @Override
    protected Map<String, ExcelDataType> simpleDataTypeMap() {
        Map<String, ExcelDataType> dataTypeMap = new HashMap<>();
        dataTypeMap.put(ExcelHeaders.STOCK_CODE, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.STOCK_NAME, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.EXCHANGE_NAME, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.BROKER_NAME, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.ACCOUNT_HOLDER, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.SEGMENT, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.ACCOUNT_TYPE, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.ASSET_TYPE, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.MATURITY_DATE, ExcelDataType.LOCAL_DATE);
        dataTypeMap.put(ExcelHeaders.PRICE, ExcelDataType.DOUBLE);
        dataTypeMap.put(ExcelHeaders.QUANTITY, ExcelDataType.DOUBLE);
        dataTypeMap.put(ExcelHeaders.TRANSACTION_TYPE, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.TRANSACTION_DATE, ExcelDataType.LOCAL_DATE);
        dataTypeMap.put(ExcelHeaders.BROKER_CHARGES, ExcelDataType.DOUBLE);
        dataTypeMap.put(ExcelHeaders.MISC_CHARGES, ExcelDataType.DOUBLE);
        dataTypeMap.put(ExcelHeaders.ORDER_ID, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.ORDER_EXECUTION_TIME, ExcelDataType.LOCAL_DATE_TIME);
        dataTypeMap.put(ExcelHeaders.TIME_ZONE, ExcelDataType.STRING);
        dataTypeMap.put(ExcelHeaders.COMMENTS, ExcelDataType.STRING);
        return dataTypeMap;
    }

    @Override
    protected AssetRequest toRequest(InputRecord inputRecord) {

        Map<String, CellDetail> record = inputRecord.getRecord();

        AssetRequest assetRequest = new AssetRequest();
        setStockCode(assetRequest, record);
        setStockName(assetRequest, record);
        setExchangeName(assetRequest, record);
        assetRequest.setBrokerName(requiredConstantOf(BrokerName.class, record, ExcelHeaders.BROKER_NAME,
                inputRecord.getRecordNumber()));
        setAccountHolder(assetRequest, record);
        constantOf(TradeSegment.class, record, ExcelHeaders.SEGMENT, inputRecord.getRecordNumber())
                .ifPresent(assetRequest::setSegment);
        constantOf(AccountType.class, record, ExcelHeaders.ACCOUNT_TYPE, inputRecord.getRecordNumber())
                .ifPresent(assetRequest::setAccountType);
        setAssetType(assetRequest, record, inputRecord.getRecordNumber());
        setPrice(assetRequest, record);
        setQuantity(assetRequest, record);
        assetRequest.setTransactionType(requiredConstantOf(TransactionType.class, record, ExcelHeaders.TRANSACTION_TYPE,
                inputRecord.getRecordNumber()));
        setTransactionDate(assetRequest, record);
        setMaturityDate(assetRequest, record, inputRecord.getRecordNumber());
        setBrokerCharges(assetRequest, record);
        setMiscCharges(assetRequest, record);
        setBrokerOrderId(assetRequest, record);
        setBrokerOrderTime(assetRequest, record);
        setTimezoneId(assetRequest, record);
        setComment(assetRequest, record);

        return assetRequest;
    }

    private static void setStockCode(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.STOCK_CODE);
        assetRequest.setStockCode((String) cellDetail.getCellValue());
    }

    private static void setStockName(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.STOCK_NAME);
        assetRequest.setStockName((String) cellDetail.getCellValue());
    }

    private static void setExchangeName(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.EXCHANGE_NAME);
        assetRequest.setExchangeName((String) cellDetail.getCellValue());
    }

    /**
     * The holder the row names; a workbook without the column, or a blank cell, names none, as a JSON trade without one
     * does (W-285, D-605).
     */
    private static void setAccountHolder(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.getOrDefault(ExcelHeaders.ACCOUNT_HOLDER, CellDetail.def());
        assetRequest.setAccountHolder((String) cellDetail.getCellValue());
    }

    /**
     * The constant a cell names. A blank cell names none, so the request keeps the default a JSON trade without it gets;
     * a word that is not a constant is refused with its row and the words accepted (W-301, D-607).
     */
    private static <E extends Enum<E>> Optional<E> constantOf(Class<E> type, Map<String, CellDetail> record,
                                                              String header, Integer recordNumber) {
        String word = (String) record.getOrDefault(header, CellDetail.def()).getCellValue();
        if (word == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, word));
        } catch (IllegalArgumentException notAConstant) {
            throw new BadRequestException("Row " + recordNumber + ", " + header + ": \"" + word + "\" is not one of "
                    + Arrays.toString(type.getEnumConstants()));
        }
    }

    /**
     * The constant a required cell names. These columns have no JSON default, so a blank cell or an absent column is
     * refused with its row rather than stored as none (W-302, D-609 C1a).
     */
    private static <E extends Enum<E>> E requiredConstantOf(Class<E> type, Map<String, CellDetail> record,
                                                            String header, Integer recordNumber) {
        return constantOf(type, record, header, recordNumber).orElseThrow(() -> new BadRequestException("Row "
                + recordNumber + ", " + header + ": blank; one of " + Arrays.toString(type.getEnumConstants())
                + " is needed"));
    }

    /** The row's asset type; a land or building is refused with its row, as the JSON paths refuse it (W-088g5). */
    private static void setAssetType(AssetRequest assetRequest, Map<String, CellDetail> record, Integer recordNumber) {

        AssetType assetType = requiredConstantOf(AssetType.class, record, ExcelHeaders.ASSET_TYPE, recordNumber);
        if (assetType == AssetType.LAND_OR_BUILDING) {
            throw new BadRequestException("Row " + recordNumber + ": " + AssetType.NOT_A_BROKER_TRADE);
        }
        assetRequest.setAssetType(assetType);
    }

    /**
     * A Gold Bond's maturity is a fact of the tranche it belongs to; with no instrument data to read it from, the row
     * must carry it rather than have one assumed (D-315 T5).
     */
    private static void setMaturityDate(AssetRequest assetRequest, Map<String, CellDetail> record, Integer recordNumber) {

        CellDetail cellDetail = record.getOrDefault(ExcelHeaders.MATURITY_DATE, CellDetail.def());
        LocalDate maturityDate = (LocalDate) cellDetail.getCellValue();
        if (maturityDate == null && assetRequest.getAssetType() == AssetType.GOLD_BOND) {
            throw new BadRequestException("Row " + recordNumber + ": a GOLD_BOND row needs its " + ExcelHeaders.MATURITY_DATE + "; it is not assumed");
        }
        assetRequest.setMaturityDate(maturityDate);
    }

    private static void setPrice(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.PRICE);
        assetRequest.setPrice((Double) cellDetail.getCellValue());
    }

    private static void setQuantity(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.QUANTITY);
        assetRequest.setQuantity((Double) cellDetail.getCellValue());
    }

    private static void setTransactionDate(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.TRANSACTION_DATE);
        assetRequest.setTransactionDate((LocalDate) cellDetail.getCellValue());
    }

    private static void setBrokerCharges(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.BROKER_CHARGES);
        assetRequest.setBrokerCharges((Double) cellDetail.getCellValue());
    }

    private static void setMiscCharges(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.MISC_CHARGES);
        assetRequest.setMiscCharges((Double) cellDetail.getCellValue());
    }

    private static void setBrokerOrderId(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.getOrDefault(ExcelHeaders.ORDER_ID, CellDetail.def());
        assetRequest.setOrderId((String) cellDetail.getCellValue());
    }

    private static void setBrokerOrderTime(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.getOrDefault(ExcelHeaders.ORDER_EXECUTION_TIME, CellDetail.def());
        assetRequest.setOrderExecutionDateTime((LocalDateTime) cellDetail.getCellValue());
    }

    private static void setTimezoneId(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.getOrDefault(ExcelHeaders.TIME_ZONE, CellDetail.def());
        assetRequest.setTimezoneId((String) cellDetail.getCellValue());
    }

    private static void setComment(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.COMMENTS);
        assetRequest.setComment((String) TOptional.map1(cellDetail, CellDetail::getCellValue));
    }

    @Override
    public ExcelParser getExcelParser() {
        return new AssetRequestParserImpl(quarter);
    }
}
