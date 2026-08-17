package com.thiru.wealthlens.shared.util.parser;

import com.thiru.wealthlens.shared.dto.enums.ExcelDataType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class CellDetail {

    private ExcelDataType excelDataType;
    private Object cellValue;

    public static CellDetail def() {
        return CellDetail.of(ExcelDataType.NULL, null);
    }
}
