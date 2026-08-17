package com.thiru.wealthlens.portfolio.service.parser.model;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface RequestParser<RequestType> {

    List<RequestType> parse(MultipartFile file, List<String> errors);

    ExcelParser getExcelParser();

}
