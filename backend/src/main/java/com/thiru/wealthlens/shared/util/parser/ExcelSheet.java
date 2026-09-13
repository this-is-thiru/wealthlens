package com.thiru.wealthlens.shared.util.parser;

import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * One sheet: its name, its columns, and the rows to write.
 *
 * <p>The type parameter is what makes a workbook of differently-typed sheets safe. {@link
 * ExcelWorkbooks} holds them as {@code ExcelSheet<?>} and cannot pair a column with the wrong row,
 * because the pairing happens in here, where {@code T} is still known.
 */
public record ExcelSheet<T>(String name, List<ExcelColumn<T>> columns, List<T> rows) {

    /** Header row from {@code columns}; one data row per item, from the same list. */
    void writeTo(XSSFWorkbook workbook, CellStyle headerStyle, CellStyle dateStyle) {
        Sheet sheet = workbook.createSheet(name);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).header());
            cell.setCellStyle(headerStyle);
        }

        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < columns.size(); c++) {
                write(row.createCell(c), columns.get(c).value().apply(rows.get(r)), dateStyle);
            }
        }
    }

    /**
     * Renders one value.
     *
     * <p>An unmapped type falls back to its string form rather than throwing. One of the mechanisms
     * this replaces threw {@code IllegalArgumentException} here, which {@code ControllerAdviser}
     * maps to 400 — reporting a programming error to the caller as their bad request, and failing a
     * whole export over a single cell.
     */
    private static void write(Cell cell, Object value, CellStyle dateStyle) {
        switch (value) {
            case null -> { /* an absent optional value; leave the cell blank */ }
            case String text -> cell.setCellValue(text);
            case Double number -> cell.setCellValue(number);
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean flag -> cell.setCellValue(flag);
            case LocalDate date -> {
                cell.setCellStyle(dateStyle);
                cell.setCellValue(date);
            }
            case Enum<?> constant -> cell.setCellValue(constant.name());
            default -> cell.setCellValue(String.valueOf(value));
        }
    }
}
