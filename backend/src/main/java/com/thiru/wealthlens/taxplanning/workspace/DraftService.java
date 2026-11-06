package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.protection.TaxAccessLog;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Workspaces, drafts and their revisions ({@code DR 11} F2, F3, F5). Every path first proves the profile is this account's. A save
 * writes its revision before the draft: the unique revision key is what turns two racing saves into one winner, and the loser's
 * failure ends its transaction, so the controller asks for the conflict afterwards.
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
    private final TaxAccessLog taxAccessLog;
    private final Clock clock;

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

    /** The year's drafts, each read recorded: a draft's inputs carry claims such as medical spend ({@code DR 14} O1.1). */
    public List<DraftView> drafts(String email, String profileId, int taxYearStart) {
        List<FilingDraft> drafts = draftRepository.findByWorkspaceId(workspace(email, profileId, taxYearStart).getId());
        drafts.forEach(draft -> taxAccessLog.record(email, ReadSubject.DRAFT, draft.getId()));
        return drafts.stream().map(DraftView::of).toList();
    }

    /** The draft's figures are current again: it was just computed under the shipped pack (W-138). */
    public void clearStaleness(String draftId) {
        draftRepository.findById(draftId).filter(draft -> draft.getStaleness() != null).ifPresent(draft -> {
            draft.setStaleness(null);
            draftRepository.save(draft);
        });
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

    /**
     * The draft and its document, if this account owns it.
     *
     * @throws BadRequestException otherwise
     */
    @Transactional(readOnly = true)
    public OwnedDraft ownedDraft(String email, String draftId) {
        FilingDraft draft = owned(email, draftId);
        return new OwnedDraft(draft, DraftInputsMapper.read(draft.getInputsJson()));
    }

    /** A draft with its document read. */
    public record OwnedDraft(FilingDraft draft, DraftInputs inputs) {
    }

    public DraftView draft(String email, String draftId) {
        FilingDraft draft = owned(email, draftId);
        taxAccessLog.record(email, ReadSubject.DRAFT, draftId);
        return DraftView.of(draft);
    }

    /**
     * Applies {@code patch} if {@code expectedRevision} is current.
     *
     * @throws DraftConflictException if it is not, or the draft is frozen, carrying the draft as it stands
     * @throws BadRequestException if the result would not bind
     */
    public DraftView patch(String email, String draftId, int expectedRevision, JsonNode patch) {
        FilingDraft draft = owned(email, draftId);
        if (!draft.getStatus().isEditable()) {
            throw new DraftConflictException("A draft that is " + draft.getStatus() + " cannot be edited", DraftView.of(draft),
                    List.of());
        }
        if (draft.getCurrentRevision() != expectedRevision) {
            throw stale(draft, expectedRevision, patch);
        }
        JsonNode inputs = JsonPatchApplier.apply(DraftInputsMapper.tree(draft.getInputsJson()), patch);
        DraftInputsMapper.requireBinds(inputs);
        boolean reopened = draft.getStatus() == DraftStatus.READY;
        DraftRevision revision = revision(draft, expectedRevision + 1, email, reopened);
        revision.setChangeSetJson(DraftInputsMapper.text(patch));
        revisionRepository.insert(revision);
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
        taxAccessLog.record(email, ReadSubject.DRAFT_REVISION, draftId);
        return state;
    }

    /** Moves the draft along its life cycle; acting on it with a regime records that election ({@code DR 17} L3). */
    public DraftView changeStatus(String email, String draftId, DraftStatusRequest request) {
        FilingDraft draft = owned(email, draftId);
        if (request.status() == null || !draft.getStatus().canBecome(request.status())) {
            throw new BadRequestException("A draft that is " + draft.getStatus() + " cannot become " + request.status());
        }
        draft.moveTo(request.status(), LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
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

    /**
     * The conflict for a save that lost a race, read after that save's transaction has ended: inside it, nothing more can be read.
     */
    @Transactional(readOnly = true)
    public DraftConflictException.DraftConflict conflictAfterRace(String email, String draftId, int expectedRevision, JsonNode patch) {
        return stale(owned(email, draftId), expectedRevision, patch).conflict();
    }

    private DraftConflictException stale(FilingDraft current, int expectedRevision, JsonNode patch) {
        List<String> changed = revisionRepository.findByDraftIdOrderByRevisionNumber(current.getId()).stream()
                .filter(revision -> revision.getRevisionNumber() > expectedRevision && revision.getChangeSetJson() != null)
                .flatMap(revision -> pathsIn(DraftInputsMapper.tree(revision.getChangeSetJson())).stream())
                .toList();
        List<String> conflicting = pathsIn(patch).stream()
                .filter(path -> changed.stream().anyMatch(other -> overlap(path, other)))
                .toList();
        return new DraftConflictException("This draft has moved on to revision " + current.getCurrentRevision(),
                DraftView.of(current), conflicting);
    }

    private static List<String> pathsIn(JsonNode patch) {
        List<String> paths = new ArrayList<>();
        patch.forEach(operation -> paths.add(operation.path("path").asString("")));
        return paths;
    }

    /** Whether one path is the other or lies inside it; an array append overlaps the whole array's later appends. */
    private static boolean overlap(String one, String other) {
        return one.equals(other) || one.startsWith(other + "/") || other.startsWith(one + "/");
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
