package com.thiru.wealthlens.portfolio.service.parser.model;

import com.thiru.wealthlens.shared.dto.InputRecord;
import com.thiru.wealthlens.shared.dto.InputRecords;
import com.thiru.wealthlens.shared.dto.enums.ExcelDataType;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TOptional;
import com.thiru.wealthlens.shared.util.parser.CellDetail;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
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
    public InputRecords parse(MultipartFile file, Map<String, ExcelDataType> dataTypeMap, List<String> errors) {

        try {
            XSSFWorkbook excelWorkbook = new XSSFWorkbook(file.getInputStream());
            var sheetIndex = sheetIndex(quarter);
            XSSFSheet transactionsSheet = excelWorkbook.getSheetAt(sheetIndex);

            InputRecords inputRecords = new InputRecords();

            int rowIndex = 0;
            for (Row row : transactionsSheet) {
                if (rowIndex == 0) {
                    List<String> fileHeaders = extractHeaders(row);
                    inputRecords.setHeaders(fileHeaders);
                } else {

                    InputRecord inputRecord = new InputRecord();
                    Map<String, CellDetail> record = extractRows(rowIndex, row, inputRecords.getHeaders(), dataTypeMap, errors);
                    inputRecord.setRecord(record);
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

    private static Map<String, CellDetail> extractRows(int rowIndex, Row row, List<String> fileHeaders, Map<String, ExcelDataType> dataTypeMap, List<String> errors) {

        Iterator<Cell> cellIterator = row.cellIterator();
        Map<String, CellDetail> record = new HashMap<>();

        int columnIndex = 0;
        while (cellIterator.hasNext()) {
            Cell cell = cellIterator.next();
            String cellHeader = fileHeaders.get(columnIndex);
            CellDetail cellDetail = getCellDetail(cell, cellHeader, dataTypeMap);
            record.put(cellHeader, cellDetail);
            columnIndex++;
        }
        if (record.size() != fileHeaders.size()) {
            errors.add("Invalid number of columns in row: " + rowIndex + ", headers count: " + fileHeaders.size() + ", actual count: " + record.size());
            return null;
        }

        log.info("Parsed Record: {}", record);
        return record;
    }

    private static CellDetail getCellDetail(Cell cell, String cellHeader, Map<String, ExcelDataType> dataTypeMap) {

        try {
            ExcelDataType excelDataType = dataTypeMap.getOrDefault(cellHeader, ExcelDataType.NULL);

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
        } catch (Exception e) {
            log.error("Error while parsing excel file error: {}, cellHeader: {}", e, cellHeader);
            throw new BadRequestException(e.getMessage());
        }
    }

    private static int sheetIndex(String quarter) {
        if (quarter == null || !ALLOWED_QUARTERS.contains(quarter)) {
            throw new BadRequestException("Invalid quarter: " + quarter + ", Allowed quarters: " + ALLOWED_QUARTERS);
        }

        return ALLOWED_QUARTERS.indexOf(quarter);
    }
}
