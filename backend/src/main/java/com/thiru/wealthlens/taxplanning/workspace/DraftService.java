package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Workspaces, drafts and their revisions ({@code DR 11} F2, F3, F5). Every path first proves the profile is this account's. A save
 * writes its revision before the draft: the unique revision key is what turns two racing saves into one winner.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DraftService {

    private final TaxProfileService taxProfileService;
    private final TaxYearWorkspaceRepository workspaceRepository;
    private final FilingDraftRepository draftRepository;
    private final DraftRevisionRepository revisionRepository;
    private final RegimeElectionHistoryRepository electionHistoryRepository;

    /** The profile's workspace for the year, made on first asking. */
    public TaxYearWorkspace workspace(String email, String profileId, int taxYearStart) {
        taxProfileService.owned(email, profileId);
        return workspaceRepository.findByProfileIdAndTaxYearStart(profileId, taxYearStart)
                .orElseGet(() -> create(profileId, taxYearStart));
    }

    private TaxYearWorkspace create(String profileId, int taxYearStart) {
        TaxYearWorkspace workspace = new TaxYearWorkspace();
        workspace.setId(UUID.randomUUID().toString());
        workspace.setProfileId(profileId);
        workspace.setTaxYearStart(taxYearStart);
        return workspaceRepository.save(workspace);
    }

    public List<DraftView> drafts(String email, String profileId, int taxYearStart) {
        return draftRepository.findByWorkspaceId(workspace(email, profileId, taxYearStart).getId()).stream()
                .map(DraftView::of).toList();
    }

    /** A blank draft at revision nil; the workspace's first becomes its primary. */
    public DraftView createDraft(String email, String profileId, int taxYearStart, String label) {
        if (label == null || label.isBlank()) {
            throw new BadRequestException("A draft needs a label");
        }
        TaxYearWorkspace workspace = workspace(email, profileId, taxYearStart);
        FilingDraft draft = new FilingDraft();
        draft.setId(UUID.randomUUID().toString());
        draft.setWorkspaceId(workspace.getId());
        draft.setProfileId(profileId);
        draft.setTaxYearStart(taxYearStart);
        draft.setLabel(label);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setInputsJson(DraftInputsMapper.blank());
        draftRepository.save(draft);
        DraftRevision first = revision(draft, 0, email, false);
        first.setFullStateJson(draft.getInputsJson());
        revisionRepository.save(first);
        if (workspace.getPrimaryDraftId() == null) {
            workspace.setPrimaryDraftId(draft.getId());
            workspaceRepository.save(workspace);
        }
        return DraftView.of(draft);
    }

    @Transactional(readOnly = true)
    public DraftView draft(String email, String draftId) {
        return DraftView.of(owned(email, draftId));
    }

    /**
     * Applies {@code patch} if {@code expectedRevision} is current.
     *
     * @throws StaleDraftException if it is not, carrying the draft as it stands
     * @throws BadRequestException if the draft is frozen or the result would not bind
     */
    public DraftView patch(String email, String draftId, int expectedRevision, JsonNode patch) {
        FilingDraft draft = owned(email, draftId);
        if (!draft.getStatus().isEditable()) {
            throw new BadRequestException("A draft that is " + draft.getStatus() + " cannot be edited");
        }
        if (draft.getCurrentRevision() != expectedRevision) {
            throw new StaleDraftException(DraftView.of(draft));
        }
        JsonNode inputs = JsonPatchApplier.apply(DraftInputsMapper.tree(draft.getInputsJson()), patch);
        DraftInputsMapper.requireBinds(inputs);
        boolean reopened = draft.getStatus() == DraftStatus.READY;
        DraftRevision revision = revision(draft, expectedRevision + 1, email, reopened);
        revision.setChangeSetJson(DraftInputsMapper.text(patch));
        try {
            revisionRepository.insert(revision);
        } catch (DuplicateKeyException e) {
            throw new StaleDraftException(DraftView.of(owned(email, draftId)));
        }
        draft.setInputsJson(DraftInputsMapper.text(inputs));
        draft.setCurrentRevision(expectedRevision + 1);
        draft.setStatus(DraftStatus.DRAFT);
        return DraftView.of(draftRepository.save(draft));
    }

    @Transactional(readOnly = true)
    public List<DraftRevision> revisions(String email, String draftId) {
        owned(email, draftId);
        return revisionRepository.findByDraftIdOrderByRevisionNumber(draftId);
    }

    /** The inputs as they stood at {@code number}, by replay from revision nil. */
    @Transactional(readOnly = true)
    public JsonNode inputsAt(String email, String draftId, int number) {
        JsonNode state = null;
        for (DraftRevision revision : revisions(email, draftId)) {
            if (revision.getRevisionNumber() > number) {
                break;
            }
            state = revision.getFullStateJson() != null ? DraftInputsMapper.tree(revision.getFullStateJson())
                    : JsonPatchApplier.apply(state, DraftInputsMapper.tree(revision.getChangeSetJson()));
        }
        if (state == null || number > owned(email, draftId).getCurrentRevision()) {
            throw new BadRequestException("This draft has no revision " + number);
        }
        return state;
    }

    /** Moves the draft along its life cycle; acting on it with a regime records that election ({@code DR 17} L3). */
    public DraftView changeStatus(String email, String draftId, DraftStatusRequest request) {
        FilingDraft draft = owned(email, draftId);
        if (request.status() == null || !draft.getStatus().canBecome(request.status())) {
            throw new BadRequestException("A draft that is " + draft.getStatus() + " cannot become " + request.status());
        }
        draft.setStatus(request.status());
        if (request.status() == DraftStatus.ACTED_ON && request.regimeCode() != null) {
            draft.setRegimeElection(request.regimeCode());
            recordElection(draft, request.regimeCode());
        }
        return DraftView.of(draftRepository.save(draft));
    }

    private void recordElection(FilingDraft draft, String regimeCode) {
        RegimeElectionHistory history = electionHistoryRepository
                .findByProfileIdAndRegimeCode(draft.getProfileId(), regimeCode)
                .orElseGet(() -> {
                    RegimeElectionHistory created = new RegimeElectionHistory();
                    created.setId(UUID.randomUUID().toString());
                    created.setProfileId(draft.getProfileId());
                    created.setRegimeCode(regimeCode);
                    return created;
                });
        history.elect(draft.getTaxYearStart(), draft.getId());
        electionHistoryRepository.save(history);
    }

    /** Makes this the workspace's primary draft. */
    public TaxYearWorkspace makePrimary(String email, String draftId) {
        FilingDraft draft = owned(email, draftId);
        TaxYearWorkspace workspace = workspaceRepository.findById(draft.getWorkspaceId()).orElseThrow(
                () -> new IllegalStateException("The draft " + draftId + " has no workspace"));
        workspace.setPrimaryDraftId(draftId);
        return workspaceRepository.save(workspace);
    }

    private FilingDraft owned(String email, String draftId) {
        FilingDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new BadRequestException("This account holds no such draft"));
        taxProfileService.owned(email, draft.getProfileId());
        return draft;
    }

    private static DraftRevision revision(FilingDraft draft, int number, String changedBy, boolean reopened) {
        DraftRevision revision = new DraftRevision();
        revision.setId(UUID.randomUUID().toString());
        revision.setDraftId(draft.getId());
        revision.setRevisionNumber(number);
        revision.setChangedBy(changedBy);
        revision.setReopened(reopened);
        return revision;
    }
}
