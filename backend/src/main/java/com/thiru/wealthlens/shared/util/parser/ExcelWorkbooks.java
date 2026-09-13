package com.thiru.wealthlens.shared.util.parser;

import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Writes sheets to an XLSX stream.
 *
 * <p>The single place a workbook is produced. There were two, and three of the four faults fixed in
 * one of them were still present in the other a day later — a {@code CellStyle} per cell, a bare
 * {@code RuntimeException}, and a cell-type switch that threw. Fixing a bug in one copy is how two
 * copies come to differ.
 */
public final class ExcelWorkbooks {

    private ExcelWorkbooks() {
    }

    /**
     * Every sheet in one workbook.
     *
     * <p>The two styles are created once and shared by every cell that needs them. Styles are
     * workbook-scoped and XLSX caps the format table at 64k entries, so one per cell does not
     * merely waste memory — a large enough export fails to write at all.
     */
    public static ByteArrayInputStream toStream(List<ExcelSheet<?>> sheets) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook);
            for (ExcelSheet<?> sheet : sheets) {
                sheet.writeTo(workbook, headerStyle, dateStyle);
            }
            workbook.write(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the export workbook", e);
        }
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
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
