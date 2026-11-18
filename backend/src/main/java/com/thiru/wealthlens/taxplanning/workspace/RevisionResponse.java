package com.thiru.wealthlens.taxplanning.workspace;

/**
 * One save as a client lists it; the state at it is read by replay.
 *
 * @param importedFrom the salary workbook the save came from, if one (W-133b C5a)
 */
public record RevisionResponse(int revisionNumber, String changedBy, boolean reopened, java.util.Optional<WorkbookImport> importedFrom) {

    public static RevisionResponse of(DraftRevision stored) {
        return new RevisionResponse(stored.getRevisionNumber(), stored.getChangedBy(), stored.isReopened(),
                java.util.Optional.ofNullable(stored.getImportedFrom()));
    }
}
