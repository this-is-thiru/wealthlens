package com.thiru.wealthlens.portfolio.service.parser;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.service.parser.model.AbstractRequestParser;
import com.thiru.wealthlens.portfolio.service.parser.model.AssetRequestParserImpl;
import com.thiru.wealthlens.portfolio.service.parser.model.ExcelParser;
import com.thiru.wealthlens.shared.dto.InputRecord;
import com.thiru.wealthlens.shared.dto.enums.ExcelDataType;
import com.thiru.wealthlens.shared.util.collection.TOptional;
import com.thiru.wealthlens.shared.util.parser.CellDetail;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
        setBrokerName(assetRequest, record);
        setAssetType(assetRequest, record);
        setPrice(assetRequest, record);
        setQuantity(assetRequest, record);
        setTransactionType(assetRequest, record);
        setTransactionDate(assetRequest, record);
        setMaturityDate(assetRequest, record);
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

    private static void setBrokerName(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.BROKER_NAME);
        String brokerName = (String) cellDetail.getCellValue();
        assetRequest.setBrokerName(BrokerName.valueOf(brokerName));
    }

    private static void setAssetType(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.ASSET_TYPE);
        String assetType = (String) cellDetail.getCellValue();
        assetRequest.setAssetType(AssetType.valueOf(assetType));
    }

    private static void setMaturityDate(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.getOrDefault(ExcelHeaders.MATURITY_DATE, CellDetail.def());
        LocalDate maturityDate = getDefaultMaturity(assetRequest.getAssetType(), assetRequest.getTransactionDate(), (LocalDate) cellDetail.getCellValue());
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

    private static void setTransactionType(AssetRequest assetRequest, Map<String, CellDetail> record) {

        CellDetail cellDetail = record.get(ExcelHeaders.TRANSACTION_TYPE);
        String transactionType = (String) cellDetail.getCellValue();
assetRequest.setTransactionType(TransactionType.valueOf(transactionType));
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

    private static LocalDate getDefaultMaturity(AssetType assetType, LocalDate transactionDate, LocalDate maturityDate) {

        return switch (assetType) {
            case EQUITY, BOND, MUTUAL_FUND, FD, INSURANCE -> null;
            case GOLD_BOND -> maturityDate != null ? maturityDate : transactionDate.plusYears(8);
        };
    }

    @Override
    public ExcelParser getExcelParser() {
        return new AssetRequestParserImpl(quarter);
    }
}
