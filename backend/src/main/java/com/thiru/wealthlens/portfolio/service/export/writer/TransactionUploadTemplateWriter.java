package com.thiru.wealthlens.portfolio.service.export.writer;

import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.export.writer.model.AbstractExcelWorkbookWriter;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class TransactionUploadTemplateWriter extends AbstractExcelWorkbookWriter<TransactionEntity> {

    private static final String EMAIL = "email";
    private static final String STOCK_CODE = "stockCode";
    private static final String STOCK_NAME = "stockName";
    private static final String ASSET_TYPE = "assetType";
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String BROKER_NAME = "brokerName";
    private static final String TRANSACTION_TYPE = "transactionType";
    private static final String STOCK_QUANTITY = "quantity";
    private static final String STOCK_PRICE = "price";
    private static final String MATURITY_DATE = "maturityDate";
    private static final String BROKER_CHARGES = "brokerCharges";
    private static final String MISC_CHARGES = "miscCharges";
    private static final String TRANSACTION_DATE = "transactionDate";
    private static final String COMMENTS = "comments";


    public TransactionUploadTemplateWriter(String sheetName, List<String> columnFields) {
        super(sheetName, columnFields);
    }

    @Override
    protected List<String> orderedColumns() {
        return List.of(EMAIL, STOCK_CODE, STOCK_NAME, ASSET_TYPE, EXCHANGE_NAME, BROKER_NAME, TRANSACTION_TYPE,
                STOCK_QUANTITY, STOCK_PRICE, TRANSACTION_DATE, MATURITY_DATE, BROKER_CHARGES, MISC_CHARGES, COMMENTS);
    }

    @Override
    protected Map<String, String> simpleColumnHeaders() {
        Map<String, String> simpleColumnHeaders = new HashMap<>();
        simpleColumnHeaders.put(EMAIL, ExcelHeaders.EMAIL);
        simpleColumnHeaders.put(STOCK_CODE, ExcelHeaders.STOCK_CODE);
        simpleColumnHeaders.put(STOCK_NAME, ExcelHeaders.STOCK_NAME);
        simpleColumnHeaders.put(ASSET_TYPE, ExcelHeaders.ASSET_TYPE);
        simpleColumnHeaders.put(EXCHANGE_NAME, ExcelHeaders.EXCHANGE_NAME);
        simpleColumnHeaders.put(BROKER_NAME, ExcelHeaders.BROKER_NAME);
        simpleColumnHeaders.put(TRANSACTION_TYPE, ExcelHeaders.TRANSACTION_TYPE);
        simpleColumnHeaders.put(STOCK_QUANTITY, ExcelHeaders.QUANTITY);
        simpleColumnHeaders.put(STOCK_PRICE, ExcelHeaders.PRICE);
        simpleColumnHeaders.put(TRANSACTION_DATE, ExcelHeaders.TRANSACTION_DATE);
        simpleColumnHeaders.put(MATURITY_DATE, ExcelHeaders.MATURITY_DATE);
        simpleColumnHeaders.put(BROKER_CHARGES, ExcelHeaders.BROKER_CHARGES);
        simpleColumnHeaders.put(MISC_CHARGES, ExcelHeaders.MISC_CHARGES);
        simpleColumnHeaders.put(COMMENTS, ExcelHeaders.COMMENTS);
        return simpleColumnHeaders;
    }

    @Override
    protected Map<String, Function<TransactionEntity, Object>> simpleColumnValueMap() {

        Map<String, Function<TransactionEntity, Object>> simpleColumnValueMap = new HashMap<>();
        simpleColumnValueMap.put(EMAIL, TransactionEntity::getEmail);
        simpleColumnValueMap.put(STOCK_CODE, TransactionEntity::getStockCode);
        simpleColumnValueMap.put(STOCK_NAME, TransactionEntity::getStockName);
        simpleColumnValueMap.put(ASSET_TYPE, (transaction) -> transaction.getAssetType().name());
        simpleColumnValueMap.put(EXCHANGE_NAME, TransactionEntity::getExchangeName);
        simpleColumnValueMap.put(BROKER_NAME, (transaction) -> transaction.getBrokerName().name());
        simpleColumnValueMap.put(TRANSACTION_TYPE, (transaction) -> transaction.getTransactionType().name());
        simpleColumnValueMap.put(STOCK_QUANTITY, TransactionEntity::getQuantity);
        simpleColumnValueMap.put(STOCK_PRICE, TransactionEntity::getPrice);
        simpleColumnValueMap.put(MATURITY_DATE, TransactionEntity::getMaturityDate);
        simpleColumnValueMap.put(BROKER_CHARGES, TransactionEntity::getBrokerCharges);
        simpleColumnValueMap.put(MISC_CHARGES, TransactionEntity::getMiscCharges);
        simpleColumnValueMap.put(TRANSACTION_DATE, TransactionEntity::getTransactionDate);
        simpleColumnValueMap.put(COMMENTS, (transaction) -> "comments");
        return simpleColumnValueMap;
    }
}
