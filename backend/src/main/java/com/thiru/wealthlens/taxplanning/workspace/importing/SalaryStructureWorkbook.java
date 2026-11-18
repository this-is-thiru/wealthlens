package com.thiru.wealthlens.taxplanning.workspace.importing;

import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import tools.jackson.databind.json.JsonMapper;

/**
 * The child process that reads an uploaded salary workbook (F28; W-133a C2a, C3a): the only code that hands its untrusted bytes to
 * Apache POI. It reads the first sheet: the header row by name ({@value #COMPONENT}, {@value #ANNUAL}, optionally
 * {@value #MONTHLY}, in any case and order), then each row with anything in it, at most {@value #MAX_ROWS}. It writes one
 * {@link WorkbookRows} as JSON to stdout and exits. It logs nothing: a salary is not for the log (O1.1).
 */
public final class SalaryStructureWorkbook {

    static final String COMPONENT = "Component";
    static final String ANNUAL = "Annual Amount";
    static final String MONTHLY = "Monthly Amount";
    /** A salary structure is tens of rows (W-133a's number, the owner's to change). */
    static final int MAX_ROWS = 200;

    private SalaryStructureWorkbook() {
    }

    /** @param args unused: a workbook is read one way */
    public static void main(String[] args) throws IOException {
        WorkbookRows reply = read(System.in.readAllBytes());
        // The reply is the process's output, not a log line: FileDescriptor.out, so no logger can interleave with it.
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            JsonMapper.builder().build().writeValue(out, reply);
        }
    }

    static WorkbookRows read(byte[] workbook) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            if (book.getNumberOfSheets() == 0) {
                return unreadable("The workbook has no sheet");
            }
            Sheet sheet = book.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return unreadable("The first sheet is empty");
            }
            Map<String, Integer> columns = columns(header);
            Optional<Integer> component = Optional.ofNullable(columns.get(key(COMPONENT)));
            Optional<Integer> annual = Optional.ofNullable(columns.get(key(ANNUAL)));
            Optional<Integer> monthly = Optional.ofNullable(columns.get(key(MONTHLY)));
            if (component.isEmpty()) {
                return unreadable("The first row names no \"" + COMPONENT + "\" column");
            }
            if (annual.isEmpty() && monthly.isEmpty()) {
                return unreadable("The first row names neither \"" + ANNUAL + "\" nor \"" + MONTHLY + "\"");
            }
            return rows(sheet, header.getRowNum(), component.get(), annual, monthly);
        }
    }

    private static WorkbookRows rows(Sheet sheet, int headerRow, int component, Optional<Integer> annual, Optional<Integer> monthly) {
        List<WorkbookRows.Row> rows = new ArrayList<>();
        for (int at = headerRow + 1; at <= sheet.getLastRowNum(); at++) {
            Row row = sheet.getRow(at);
            if (row == null) {
                continue;
            }
            WorkbookRows.Row read = new WorkbookRows.Row(at + 1, text(row.getCell(component)), annual.map(column -> text(row.getCell(column))).orElse(""),
                    monthly.map(column -> text(row.getCell(column))).orElse(""));
            if (read.label().isEmpty() && read.annual().isEmpty() && read.monthly().isEmpty()) {
                continue;
            }
            if (rows.size() == MAX_ROWS) {
                return unreadable("More than " + MAX_ROWS + " rows: a salary structure is tens of rows");
            }
            rows.add(read);
        }
        return new WorkbookRows(WorkbookRows.Outcome.READ, rows, "");
    }

    private static Map<String, Integer> columns(Row header) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            String name = text(cell);
            if (!name.isEmpty()) {
                columns.putIfAbsent(key(name), cell.getColumnIndex());
            }
        }
        return columns;
    }

    private static String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** A cell as the text it holds: a number in plain digits, a formula by its last computed value, a blank as empty. */
    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static WorkbookRows unreadable(String reason) {
        return new WorkbookRows(WorkbookRows.Outcome.UNREADABLE, List.of(), reason);
    }
}
