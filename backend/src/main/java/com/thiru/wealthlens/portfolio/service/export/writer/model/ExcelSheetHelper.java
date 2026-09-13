package com.thiru.wealthlens.portfolio.service.export.writer.model;

import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelSheetHelper {

    public static XSSFSheet createNewSheet(XSSFWorkbook workbook, List<String> headers, String sheetName) {

        XSSFSheet sheet = workbook.createSheet(sheetName);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(headers.get(i));

            // Create cell style with bold font
            CellStyle headerCellStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerCellStyle.setFont(headerFont);

            // Apply style to header cells
            headerCell.setCellStyle(headerCellStyle);
        }

        return sheet;
    }
}
