package com.thiru.wealthlens.taxplanning.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

/** A draft as a client reads it; {@code revision} is the token {@code If-Match} carries. */
public record DraftView(String id, String workspaceId, String label, DraftStatus status, int revision, String regimeElection,
        JsonNode inputs, @JsonInclude(JsonInclude.Include.NON_NULL) DraftStaleness staleness) {

    static DraftView of(FilingDraft draft) {
        return new DraftView(draft.getId(), draft.getWorkspaceId(), draft.getLabel(), draft.getStatus(),
                draft.getCurrentRevision(), draft.getRegimeElection(), DraftInputsMapper.tree(draft.getInputsJson()),
                draft.getStaleness());
    }
}
