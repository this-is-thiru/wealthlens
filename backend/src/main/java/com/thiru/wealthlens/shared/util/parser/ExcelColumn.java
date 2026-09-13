package com.thiru.wealthlens.shared.util.parser;

import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.money.TMoney;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * One column of an export: its heading, and where the value comes from.
 *
 * <p><b>Why the two travel together.</b> The headings used to live in one class as a
 * {@code String[]} and the values in another as {@code createCell(0)}…{@code createCell(13)}. The
 * correspondence between them was positional and unenforced, so inserting a column in the middle
 * shifted every value below it under the wrong heading — no compile error, no failing test, just a
 * wrong spreadsheet in somebody's hands. Pairing them makes that drift unrepresentable.
 *
 * <p><b>Still declared, not derived.</b> Reflecting over a DTO's fields would have removed the
 * duplication too, and would have been worse: it makes the file format an accident of field
 * declaration order, so adding a field silently inserts or moves a column in a spreadsheet someone
 * has built formulas against. An export is a published contract. Every column here is written down
 * on purpose, and adding one is a deliberate line rather than a side effect.
 */
public record ExcelColumn<T>(String header, Function<T, Object> value) {

    /** A plain string column. Enum values should use {@link #text} with {@code Enum::name}. */
    public static <T> ExcelColumn<T> text(String header, Function<T, String> value) {
        return new ExcelColumn<>(header, value::apply);
    }

    /**
     * A rupee amount, rounded the way the rest of the application rounds.
     *
     * <p>Null becomes zero: a blank cell where a figure belongs reads as missing data rather than
     * as nothing charged, and several of these come from boxed fields on the response.
     */
    public static <T> ExcelColumn<T> money(String header, Function<T, Double> value) {
        return new ExcelColumn<>(header, row -> {
            Double amount = value.apply(row);
            return TMoney.scale(amount == null ? 0.0 : amount);
        });
    }

    /** A quantity. Rounded like money because it is displayed to the same precision. */
    public static <T> ExcelColumn<T> quantity(String header, Function<T, Double> value) {
        return money(header, value);
    }

    /** A date, formatted by the application's own date format. */
    public static <T> ExcelColumn<T> date(String header, Function<T, LocalDate> value) {
        return new ExcelColumn<>(header, value::apply);
    }

    /**
     * Writes a sheet: the heading row, then one row per item, from one list of columns.
     *
     * <p>The same list drives both, which is the whole point — a heading cannot outlive the value
     * beneath it, or arrive without one.
     */
    public static <T> void write(XSSFWorkbook workbook, String sheetName,
                                 List<ExcelColumn<T>> columns, List<T> rows) {
        Sheet sheet = workbook.createSheet(sheetName);
        CellStyle headerStyle = headerStyle(workbook);
        // One date style for the sheet. Styles are workbook-scoped and XLSX caps the format table
        // at 64k, so one per cell fails to write at all once an export is large enough.
        CellStyle dateStyle = dateStyle(workbook);

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

    private static void write(Cell cell, Object value, CellStyle dateStyle) {
        switch (value) {
            case null -> { /* a genuinely absent optional value; leave the cell blank */ }
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

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle dateStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        style.setDataFormat(createHelper.createDataFormat().getFormat(TCollectionUtil.DATE_FORMAT));
        return style;
    }
}
