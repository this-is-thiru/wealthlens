package com.thiru.wealthlens.portfolio.service.parser.model;

import com.thiru.wealthlens.shared.dto.InputRecord;
import com.thiru.wealthlens.shared.dto.InputRecords;
import com.thiru.wealthlens.shared.dto.enums.ExcelDataType;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.web.multipart.MultipartFile;

public abstract class AbstractRequestParser<RequestType> implements RequestParser<RequestType> {

    private static final String EXCEL_TYPE = "text/xls";
    private static final String EXCEL_TYPE1 = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";


    protected abstract Map<String, ExcelDataType> simpleDataTypeMap();

    protected abstract RequestType toRequest(InputRecord inputRecord);

    @Override
    public List<RequestType> parse(MultipartFile file, List<String> errors) {

        validateFile(file);

        // Excel parser
        ExcelParser excelParser = this.getExcelParser();
        InputRecords parsedRecords = excelParser.parse(file, this.simpleDataTypeMap(), errors);
        return parseRequests(parsedRecords);
    }

    private static void validateFile(MultipartFile file) {
        if (!isValidExcelFile(file)) {
            throw new BadRequestException("Invalid file format");
        }
    }

    private static boolean isValidExcelFile(MultipartFile file) {
        return EXCEL_TYPE.equals(file.getContentType()) || EXCEL_TYPE1.equals(file.getContentType());
    }


    private List<RequestType> parseRequests(InputRecords records) {
        return TCollectionUtil.map(sanitizeRecords(records), this::toRequest);
    }

    private static List<InputRecord> sanitizeRecords(InputRecords records) {
        List<InputRecord> inputRecords = records.getRecords();
        return TCollectionUtil.filter(inputRecords, filterOutRecord());
    }

    private static Predicate<InputRecord> filterOutRecord() {
        return inputRecord -> inputRecord != null && inputRecord.getRecord() != null;
    }
}
