package com.thiru.wealthlens.taxplanning.workspace.importing;

import java.util.List;

/**
 * What the isolated read of a salary workbook found (W-133a): the reply {@link SalaryStructureWorkbook} writes and
 * {@link SalaryImportService} parses strictly. Cells come back as the text they hold, so the application, not the child, decides
 * what is an amount.
 *
 * @param rows each row under the header with anything in it, in sheet order, when {@code outcome} is {@code READ}
 * @param reason why the workbook could not be read, when {@code outcome} is {@code UNREADABLE}; otherwise empty
 */
public record WorkbookRows(Outcome outcome, List<Row> rows, String reason) {

    public WorkbookRows {
        rows = List.copyOf(rows);
    }

    public enum Outcome {
        READ,
        UNREADABLE
    }

    /**
     * One row of the sheet.
     *
     * @param number the row's number as the spreadsheet shows it, counting from 1
     * @param label the "Component" cell; empty where blank
     * @param annual the "Annual Amount" cell as text; empty where blank or where the sheet has no such column
     * @param monthly the "Monthly Amount" cell as text; empty where blank or where the sheet has no such column
     */
    public record Row(int number, String label, String annual, String monthly) {
    }
}
