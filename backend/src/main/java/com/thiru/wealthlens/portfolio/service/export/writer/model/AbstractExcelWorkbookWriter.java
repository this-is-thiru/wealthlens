package com.thiru.wealthlens.portfolio.service.export.writer.model;

import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.InputStreamResource;

public abstract class AbstractExcelWorkbookWriter<EntityType extends AuditableEntity> implements ExcelWorkbookWriter<EntityType> {

    private final String sheetName;
    private final List<String> columnFields;
    private final Map<String, Integer> columnIndexMap;

    public AbstractExcelWorkbookWriter(String sheetName, List<String> columnFields) {
        this.sheetName = sheetName;
        this.columnFields = columnFields.isEmpty() ? this.orderedColumns() : columnFields;
        this.columnIndexMap = this.sanitizeAndGetAsColumnIndexMap(this.columnFields);
    }

    protected abstract List<String> orderedColumns();

    protected abstract Map<String, String> simpleColumnHeaders();

    protected abstract Map<String, Function<EntityType, Object>> simpleColumnValueMap();

    @Override
    public InputStreamResource process(List<EntityType> entities) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = ExcelSheetHelper.createNewSheet(workbook, this.headers(), sheetName);
            sheetWriter(sheet, entities);
            extraSheetWriter(workbook, entities);
            workbook.write(outputStream);
            return new InputStreamResource(new ByteArrayInputStream(outputStream.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sheetWriter(Sheet sheet, List<EntityType> entities) {

        for (int rowIndex = 0; rowIndex < entities.size(); rowIndex++) {
            EntityType entityType = entities.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);
            for (String columnField : columnFields) {
                cellPopulator(row.createCell(columnIndexMap.get(columnField)), columnField, entityType);
            }
        }
    }

    protected void cellPopulator(Cell cell, String columnField, EntityType entityType) {

        var fieldExtractor = this.simpleColumnValueMap().get(columnField);
        if (fieldExtractor == null) {
            throw new BadRequestException("Column " + columnField + " not found");
        }

        Object data = fieldExtractor.apply(entityType);
        if (data == null) {
            return;
        }

        switch (data) {
            case Boolean flag:
                cell.setCellValue(flag);
                break;
            case Double num:
                cell.setCellValue(num);
                break;
            case String str:
                cell.setCellValue(str);
                break;
            case LocalDate localDate:
                setDateField(cell, localDate);
                break;
            default:
                throw new IllegalArgumentException("Cell value conversion not supported currently: " + data);
        }
    }

    private static void setDateField(Cell cell, LocalDate date) {
        CellStyle dateStyle = cell.getSheet().getWorkbook().createCellStyle();
        CreationHelper createHelper = cell.getSheet().getWorkbook().getCreationHelper();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat(TCollectionUtil.DATE_FORMAT));
        cell.setCellStyle(dateStyle);
        cell.setCellValue(date);
    }

    private Map<String, Integer> sanitizeAndGetAsColumnIndexMap(List<String> columnFields) {

        List<String> sanitizedColumnNames = TCollectionUtil.filter(columnFields, this.orderedColumns()::contains);

        Map<String, Integer> sanitizedColumnIndexMap = new HashMap<>(sanitizedColumnNames.size());
        for (int i = 0; i < sanitizedColumnNames.size(); i++) {
            sanitizedColumnIndexMap.put(sanitizedColumnNames.get(i), i);
        }
        return sanitizedColumnIndexMap;
    }

    private List<String> headers() {
        return TCollectionUtil.map(orderedColumns(), column -> simpleColumnHeaders().get(column).toUpperCase());
    }

    protected void extraSheetWriter(XSSFWorkbook workbook, List<EntityType> entities) {
    }
}
