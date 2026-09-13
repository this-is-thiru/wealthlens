package com.thiru.wealthlens.portfolio.service.export.writer.model;

import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.InputStreamResource;

/**
 * Writes a sheet from a declared column order, a header per column and an extractor per column.
 *
 * <h2>One list drives the header row and the data rows</h2>
 * It used to be two. The header row was built from {@link #orderedColumns()} — always every column —
 * while the data was written at indices derived from the caller's <em>selection</em>, numbered from
 * zero. Asking for two of thirteen columns therefore wrote thirteen headers and put the two values
 * under the first two of them: the stock code appeared beneath EMAIL, and nothing about the file
 * looked wrong. Both now walk {@code columns}, so the header cannot describe a value other than the
 * one beneath it.
 *
 * <h2>The selection is validated once, when the writer is built</h2>
 * {@code selectedColumns} arrives from the request body. An unrecognised name used to survive into
 * the write loop, miss the index map and fail as a {@code NullPointerException} — a 500 for what is
 * the caller's typo. Dropping it silently would be worse: the export would come back missing a
 * column that was asked for, and say nothing.
 */
public abstract class AbstractExcelWorkbookWriter<EntityType extends AuditableEntity> implements ExcelWorkbookWriter<EntityType> {

    private final String sheetName;

    /** The columns this export writes, in order. Headers and values both follow it. */
    private final List<String> columns;

    private final Map<String, String> headersByField;
    private final Map<String, Function<EntityType, Object>> valuesByField;

    protected AbstractExcelWorkbookWriter(String sheetName, List<String> columnFields) {
        this.sheetName = sheetName;
        // Resolved once. These used to be called per cell and per header, and a subclass builds a
        // fresh HashMap inside each — thirteen thousand of them for a thousand-row export.
        this.headersByField = Map.copyOf(simpleColumnHeaders());
        this.valuesByField = Map.copyOf(simpleColumnValueMap());
        this.columns = selectColumns(columnFields);
    }

    protected abstract List<String> orderedColumns();

    protected abstract Map<String, String> simpleColumnHeaders();

    protected abstract Map<String, Function<EntityType, Object>> simpleColumnValueMap();

    @Override
    public InputStreamResource process(List<EntityType> entities) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = ExcelSheetHelper.createNewSheet(workbook, headers(), sheetName);
            sheetWriter(sheet, entities, ExcelSheetHelper.dateStyle(workbook));
            extraSheetWriter(workbook, entities);
            workbook.write(outputStream);
            return new InputStreamResource(new ByteArrayInputStream(outputStream.toByteArray()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the " + sheetName + " export workbook", e);
        }
    }

    /**
     * The columns to export: the caller's selection, or everything when they asked for nothing.
     *
     * @throws BadRequestException if a requested column is not one this export declares
     */
    private List<String> selectColumns(List<String> requested) {
        List<String> declared = orderedColumns();
        declared.stream()
                .filter(column -> !headersByField.containsKey(column) || !valuesByField.containsKey(column))
                .findFirst()
                .ifPresent(column -> {
                    throw new IllegalStateException(getClass().getSimpleName() + " declares column '"
                            + column + "' in its order but gives it no header or no value");
                });

        if (requested == null || requested.isEmpty()) {
            return List.copyOf(declared);
        }

        List<String> unknown = TCollectionUtil.filter(requested, column -> !declared.contains(column));
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Cannot export " + unknown + ": not a column of this report."
                    + " Available columns are " + declared);
        }
        return List.copyOf(requested);
    }

    /** The header row, in the same order and of the same length as every data row. */
    private List<String> headers() {
        return TCollectionUtil.map(columns, column -> headersByField.get(column).toUpperCase());
    }

    private void sheetWriter(Sheet sheet, List<EntityType> entities, CellStyle dateStyle) {

        for (int rowIndex = 0; rowIndex < entities.size(); rowIndex++) {
            EntityType entity = entities.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);
            for (int column = 0; column < columns.size(); column++) {
                Object value = valuesByField.get(columns.get(column)).apply(entity);
                write(row.createCell(column), value, dateStyle);
            }
        }
    }

    /**
     * Renders one value.
     *
     * <p>Matches {@code ExcelColumn.write} deliberately: two export mechanisms disagreeing about
     * how a date or an enum reaches a cell would be a difference between two downloads of the same
     * data. An unmapped type falls back to its string form rather than throwing — the previous
     * {@code IllegalArgumentException} surfaced a programming error to the caller as a 400, and
     * failed the whole export over one cell.
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

    protected void extraSheetWriter(XSSFWorkbook workbook, List<EntityType> entities) {
    }
}
