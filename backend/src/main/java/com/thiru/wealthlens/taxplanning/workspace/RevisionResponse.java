package com.thiru.wealthlens.taxplanning.workspace;

/** One save as a client lists it; the state at it is read by replay. */
public record RevisionResponse(int revisionNumber, String changedBy, boolean reopened) {

    public static RevisionResponse of(DraftRevision stored) {
        return new RevisionResponse(stored.getRevisionNumber(), stored.getChangedBy(), stored.isReopened());
    }
}
