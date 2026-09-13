package com.thiru.wealthlens.portfolio.service.export.writer.model;

import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelSheetHelper {

    private ExcelSheetHelper() {
    }

    public static XSSFSheet createNewSheet(XSSFWorkbook workbook, List<String> headers, String sheetName) {

        XSSFSheet sheet = workbook.createSheet(sheetName);

        // One style for the whole header row. It used to be created per cell, which is the same
        // fault as the date style below and wasteful for exactly the same reason.
        CellStyle headerStyle = headerStyle(workbook);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(headers.get(i));
            headerCell.setCellStyle(headerStyle);
        }

        return sheet;
    }

    /**
     * The workbook's one date style.
     *
     * <p>Styles are workbook-scoped and XLSX caps the format table at 64k entries, so creating one
     * per cell does not merely waste memory — a large enough export fails to write at all. Created
     * once per workbook and handed to every date cell.
     */
    public static CellStyle dateStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        style.setDataFormat(createHelper.createDataFormat().getFormat(TCollectionUtil.DATE_FORMAT));
        return style;
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
