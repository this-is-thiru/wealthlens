package com.thiru.wealthlens.shared.util.transaction;

public class ExcelHeaders {

    public static final String EMAIL = "EMAIL";
    public static final String STOCK_CODE = "STOCK CODE";
    public static final String STOCK_NAME = "STOCK NAME";
    public static final String ORDER_ID = "ORDER ID";
    public static final String ORDER_EXECUTION_TIME = "ORDER EXECUTION TIME";
    public static final String TIME_ZONE = "TIME ZONE";
    public static final String EXCHANGE_NAME = "EXCHANGE NAME";
    public static final String BROKER_NAME = "BROKER NAME";
    public static final String ASSET_TYPE = "ASSET TYPE";
    public static final String MATURITY_DATE = "MATURITY DATE";
    public static final String PRICE = "PRICE";
    public static final String TOTAL_VALUE = "TOTAL VALUE";
    public static final String QUANTITY = "QUANTITY";
    public static final String TOTAL_QUANTITY = "TOTAL QUANTITY";
    public static final String TRANSACTION_TYPE = "TRANSACTION TYPE";
    public static final String TRANSACTION_DATE = "TRANSACTION DATE";
    public static final String BROKER_CHARGES = "BROKER CHARGES";
    public static final String MISC_CHARGES = "MISC CHARGES";
    public static final String COMMENTS = "COMMENTS";

    public static String[] getTransactionHeaders() {
        return new String[]{EMAIL, STOCK_CODE, STOCK_NAME, EXCHANGE_NAME, BROKER_NAME, ASSET_TYPE, MATURITY_DATE, PRICE,
                QUANTITY, TRANSACTION_TYPE, TRANSACTION_DATE, BROKER_CHARGES, MISC_CHARGES, COMMENTS};
    }

    public static String[] getPortfolioHeaders() {
        return new String[]{ExcelHeaders.EMAIL, ExcelHeaders.STOCK_NAME, ExcelHeaders.STOCK_CODE,
                ExcelHeaders.QUANTITY, ExcelHeaders.TOTAL_QUANTITY, ExcelHeaders.PRICE, ExcelHeaders.TOTAL_VALUE,
                ExcelHeaders.EXCHANGE_NAME, ExcelHeaders.BROKER_NAME, ExcelHeaders.ASSET_TYPE,
                ExcelHeaders.MATURITY_DATE, ExcelHeaders.BROKER_CHARGES, ExcelHeaders.MISC_CHARGES, "HIII"};
    }

    public static String[] getTransactionQuantityHeaders() {
        return new String[]{ExcelHeaders.STOCK_CODE, ExcelHeaders.BROKER_NAME, "TRANSACTION DATE", ExcelHeaders.QUANTITY};
    }
}
