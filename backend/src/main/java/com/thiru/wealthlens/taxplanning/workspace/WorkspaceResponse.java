package com.thiru.wealthlens.taxplanning.workspace;

/** A workspace as a client reads it. */
public record WorkspaceResponse(String id, String profileId, int taxYearStart, String primaryDraftId) {

    public static WorkspaceResponse of(TaxYearWorkspace stored) {
        return new WorkspaceResponse(stored.getId(), stored.getProfileId(), stored.getTaxYearStart(), stored.getPrimaryDraftId());
    }
}
