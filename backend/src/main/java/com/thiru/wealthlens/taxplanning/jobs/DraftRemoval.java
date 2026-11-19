package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxplanning.compute.DraftComputationRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevisionRepository;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a draft with everything that is only its own — revisions and computations — in one transaction, and unsets it as its
 * workspace's primary. The access log and the regime election history stay: they outlive the draft ({@code DR 14} O4, O5).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DraftRemoval {

    private final FilingDraftRepository drafts;
    private final DraftRevisionRepository revisions;
    private final DraftComputationRepository computations;
    private final TaxYearWorkspaceRepository workspaces;

    public void remove(FilingDraft draft) {
        computations.deleteByDraftId(draft.getId());
        revisions.deleteByDraftId(draft.getId());
        workspaces.findById(draft.getWorkspaceId()).filter(workspace -> draft.getId().equals(workspace.getPrimaryDraftId()))
                .ifPresent(workspace -> {
                    workspace.setPrimaryDraftId(null);
                    workspaces.save(workspace);
                });
        drafts.delete(draft);
    }
}
