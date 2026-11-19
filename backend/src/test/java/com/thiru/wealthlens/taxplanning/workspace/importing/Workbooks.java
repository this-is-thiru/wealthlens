package com.thiru.wealthlens.taxplanning.workspace.importing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Salary workbooks built in the test (W-133a): a header row, then rows of text or numbers; null leaves a cell unwritten. */
final class Workbooks {

    private Workbooks() {
    }

    static byte[] sheet(Object[]... rows) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Salary");
            for (int at = 0; at < rows.length; at++) {
                Row row = sheet.createRow(at);
                for (int column = 0; column < rows[at].length; column++) {
                    Object value = rows[at][column];
                    if (value instanceof Number number) {
                        row.createCell(column).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(column).setCellValue(value.toString());
                    }
                }
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
