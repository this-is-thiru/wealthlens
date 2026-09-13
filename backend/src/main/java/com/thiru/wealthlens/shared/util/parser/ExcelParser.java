package com.thiru.wealthlens.shared.util.parser;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Log4j2
public class ExcelParser {

    private static final String EXCEL_TYPE = "text/xls";

    public static final String ASSETS = "ASSETS";
    public static final String TRANSACTIONS = "TRANSACTIONS";
    public static final String PORTFOLIO_FILE_NAME = "portfolio.xlsx";
    public static final String TRANSACTION_FILE_NAME = "transactions.xlsx";
    public static final String HOLDINGS_FILE_NAME = "holdings.xlsx";

    public static void initialiseExcelSheet(XSSFWorkbook workbook, String[] headers, String sheetName) {

        XSSFSheet sheet = workbook.createSheet(sheetName);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(headers[i]);

            // Create cell style with bold font
            CellStyle headerCellStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerCellStyle.setFont(headerFont);

            // Apply style to header cells
            headerCell.setCellStyle(headerCellStyle);
        }
    }
}
