package com.thiru.wealthlens.portfolio.service.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thiru.wealthlens.portfolio.service.export.writer.AssetExcelWorkbookWriter;
import com.thiru.wealthlens.portfolio.service.export.writer.TransactionExcelWorkbookWriter;
import com.thiru.wealthlens.portfolio.service.export.writer.TransactionUploadTemplateWriter;
import com.thiru.wealthlens.portfolio.service.export.writer.model.ExcelWorkbookWriter;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What every workbook writer must hold to, checked against all three rather than one.
 *
 * <p>{@code AssetExcelWorkbookWriter} is the only one reached by a test with real data. These are
 * the invariants that do not need data to check, applied to the two writers that otherwise have no
 * coverage at all.
 */
@Tag("unit")
class ExcelWorkbookWriterContractTest {

    private static Stream<Arguments> writers() {
        return Stream.of(
                Arguments.of("asset",
                        (Function<List<String>, ExcelWorkbookWriter<?>>)
                                columns -> new AssetExcelWorkbookWriter("ASSETS", columns)),
                Arguments.of("transaction",
                        (Function<List<String>, ExcelWorkbookWriter<?>>)
                                columns -> new TransactionExcelWorkbookWriter("TRANSACTIONS", columns)),
                Arguments.of("upload-template",
                        (Function<List<String>, ExcelWorkbookWriter<?>>)
                                columns -> new TransactionUploadTemplateWriter("TRANSACTIONS", columns)));
    }

    /**
     * A selection of two columns produces a header row of two. It used to produce the full header
     * row whatever was selected, which is what put values under the wrong headings.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    @DisplayName("a two-column selection writes a two-column header row")
    void process_whenTwoColumnsSelected_writesTwoHeaders(
            String name, Function<List<String>, ExcelWorkbookWriter<?>> factory) throws IOException {
        // Given
        ExcelWorkbookWriter<?> writer = factory.apply(List.of("stockCode", "stockName"));

        // When
        List<String> headers = headersOf(writer);

        // Then
        assertEquals(List.of("STOCK CODE", "STOCK NAME"), headers);
    }

    /** Every writer declares a header and an extractor for each column it orders. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    @DisplayName("every declared column is complete, so the default export is writable")
    void construct_whenNoSelection_declaresEveryColumnFully(
            String name, Function<List<String>, ExcelWorkbookWriter<?>> factory) throws IOException {
        // When / Then — the constructor refuses a column missing a header or a value
        assertEquals(headersOf(factory.apply(List.of())).size(),
                headersOf(factory.apply(List.of())).stream().distinct().count(),
                "a repeated header means two columns share a heading");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    @DisplayName("an unrecognised column is refused rather than dropped or fatal")
    void construct_whenColumnIsUnknown_refuses(
            String name, Function<List<String>, ExcelWorkbookWriter<?>> factory) {
        // When / Then
        assertThrows(BadRequestException.class, () -> factory.apply(List.of("stockCode", "nope")));
    }

    @SuppressWarnings("unchecked")
    private static List<String> headersOf(ExcelWorkbookWriter<?> writer) throws IOException {
        var typed = (ExcelWorkbookWriter<com.thiru.wealthlens.shared.entity.model.AuditableEntity>) writer;
        try (InputStream in = typed.process(List.of()).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Row headerRow = workbook.getSheetAt(0).getRow(0);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                names.add(headerRow.getCell(i).getStringCellValue());
            }
            return names;
        }
    }
}
