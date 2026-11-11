package com.thiru.wealthlens.portfolio.service.parser.model;

import com.thiru.wealthlens.shared.dto.InputRecord;
import com.thiru.wealthlens.shared.dto.InputRecords;
import com.thiru.wealthlens.shared.dto.enums.ExcelDataType;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TOptional;
import com.thiru.wealthlens.shared.util.parser.CellDetail;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

@Log4j2
public class AssetRequestParserImpl implements ExcelParser {

    private static final List<String> ALLOWED_QUARTERS = List.of("Q1", "Q2", "Q3", "Q4");

    private final String quarter;

    public AssetRequestParserImpl(String quarter) {
        this.quarter = quarter;
    }

    @Override
    public InputRecords parse(MultipartFile file, Map<String, ExcelDataType> dataTypeMap) {

        try {
            XSSFWorkbook excelWorkbook = new XSSFWorkbook(file.getInputStream());
            var sheetIndex = sheetIndex(quarter);
            XSSFSheet transactionsSheet = excelWorkbook.getSheetAt(sheetIndex);

            InputRecords inputRecords = new InputRecords();

            int rowIndex = 0;
            for (Row row : transactionsSheet) {
                if (rowIndex == 0) {
                    inputRecords.setHeaders(extractHeaders(row));
                } else if (hasAnyValue(row)) {
                    InputRecord inputRecord = new InputRecord();
                    inputRecord.setRecord(extractRow(rowIndex, row, inputRecords.getHeaders(), dataTypeMap));
                    inputRecord.setRecordNumber(rowIndex);
                    inputRecords.getRecords().add(inputRecord);
                }
                rowIndex++;
            }

            return inputRecords;
        } catch (IOException e) {
            throw new BadRequestException("Data is not in valid format");
        }
    }

    private static List<String> extractHeaders(Row row) {
        Iterator<Cell> cellIterator = row.cellIterator();

        List<String> fileHeaders = new ArrayList<>();
        while (cellIterator.hasNext()) {
            Cell cell = cellIterator.next();
            fileHeaders.add(cell.getStringCellValue());
        }
        return fileHeaders;
    }

    /** A row a spreadsheet keeps after the data, formatted but empty, is not a trade (W-276 C2a). */
    private static boolean hasAnyValue(Row row) {
        for (Cell cell : row) {
            if (!isEmpty(cell)) {
                return true;
            }
        }
        return false;
    }

    /** Never written, blank, or text of only spaces: nothing was entered. */
    private static boolean isEmpty(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    /**
     * Reads cell <i>i</i> under header <i>i</i>. A spreadsheet program writes no cell for an empty one, so reading in
     * iteration order moved every later value one header left; an empty or blank cell is absent, never 0 (W-276).
     */
    private static Map<String, CellDetail> extractRow(int rowIndex, Row row, List<String> fileHeaders,
                                                      Map<String, ExcelDataType> dataTypeMap) {
        refuseValuesUnderNoHeader(rowIndex, row, fileHeaders.size());
        Map<String, CellDetail> record = new HashMap<>();
        for (int column = 0; column < fileHeaders.size(); column++) {
            String header = fileHeaders.get(column);
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            record.put(header, isEmpty(cell) ? CellDetail.def() : getCellDetail(rowIndex, cell, header, dataTypeMap));
        }
        log.info("Parsed Record: {}", record);
        return record;
    }

    private static void refuseValuesUnderNoHeader(int rowIndex, Row row, int headerCount) {
        for (Cell cell : row) {
            if (cell.getColumnIndex() >= headerCount && !isEmpty(cell)) {
                throw new BadRequestException("Row " + rowIndex + " has a value in column " + (cell.getColumnIndex() + 1)
                        + ", which has no header");
            }
        }
    }

    private static CellDetail getCellDetail(int rowIndex, Cell cell, String cellHeader, Map<String, ExcelDataType> dataTypeMap) {
        ExcelDataType excelDataType = dataTypeMap.getOrDefault(cellHeader, ExcelDataType.NULL);
        try {
            return switch (excelDataType) {
                case BOOLEAN -> CellDetail.of(ExcelDataType.BOOLEAN, cell.getBooleanCellValue());
                case INTEGER, LONG -> CellDetail.of(ExcelDataType.LONG, (long) cell.getNumericCellValue());
                case DOUBLE -> CellDetail.of(ExcelDataType.DOUBLE, cell.getNumericCellValue());
                case STRING -> CellDetail.of(ExcelDataType.STRING, cell.getStringCellValue());
                case LOCAL_DATE_TIME ->
                        CellDetail.of(ExcelDataType.LOCAL_DATE_TIME, TLocalDate.convertToDateTime(cell.getStringCellValue()));
                case LOCAL_DATE ->
                        CellDetail.of(ExcelDataType.LOCAL_DATE, TOptional.map1(cell.getLocalDateTimeCellValue(), LocalDateTime::toLocalDate));
                case ERROR -> CellDetail.of(ExcelDataType.ERROR, cell.getErrorCellValue());
                case NULL -> CellDetail.of(ExcelDataType.NULL, null);
            };
        } catch (IllegalStateException | DateTimeException misfit) {
            log.warn("Row {}, {}: {}", rowIndex, cellHeader, misfit.getMessage());
            throw new BadRequestException("Row " + rowIndex + ", " + cellHeader + ": expected " + expected(excelDataType));
        }
    }

    private static String expected(ExcelDataType excelDataType) {
        return switch (excelDataType) {
            case BOOLEAN -> "true or false";
            case INTEGER, LONG, DOUBLE -> "a number";
            case LOCAL_DATE -> "a date";
            case LOCAL_DATE_TIME -> "a date and time as text";
            case STRING -> "text";
            case ERROR, NULL -> "a value of its column's kind";
        };
    }

    private static int sheetIndex(String quarter) {
        if (quarter == null || !ALLOWED_QUARTERS.contains(quarter)) {
            throw new BadRequestException("Invalid quarter: " + quarter + ", Allowed quarters: " + ALLOWED_QUARTERS);
        }

        return ALLOWED_QUARTERS.indexOf(quarter);
    }
}
