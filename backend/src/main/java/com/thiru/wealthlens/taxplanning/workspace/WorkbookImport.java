package com.thiru.wealthlens.taxplanning.workspace;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Where a draft revision's change came from, when it came from a salary workbook (F28.4; W-133b C5a, D-775 C6a): the file and
 * what was done with each of its rows. The patch itself stays the revision's change set, so replay is unchanged.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkbookImport {

    /** The workbook's SHA-256, as 64 lowercase hex characters: what refuses the same file twice (C6a). */
    @Field("workbook_sha256")
    private String workbookSha256;

    /** Every row that became a pay line, matched or mapped, by its number in the sheet. */
    @Field("rows_applied")
    private List<Integer> rowsApplied = new ArrayList<>();

    /** The rows the user mapped to a component. */
    @Field("rows_mapped")
    private List<Integer> rowsMapped = new ArrayList<>();

    /** The rows the user chose to leave out. */
    @Field("rows_left_out")
    private List<Integer> rowsLeftOut = new ArrayList<>();
}
