package com.thiru.wealthlens.shared.util.parser;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ExcelParser {

    private static final String EXCEL_TYPE = "text/xls";

    public static final String ASSETS = "ASSETS";
    public static final String TRANSACTIONS = "TRANSACTIONS";
    public static final String PORTFOLIO_FILE_NAME = "portfolio.xlsx";
    public static final String TRANSACTION_FILE_NAME = "transactions.xlsx";
    public static final String HOLDINGS_FILE_NAME = "holdings.xlsx";
}
