package com.thiru.wealthlens.taxplanning.workspace.controller;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxplanning.workspace.DraftConflictException;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatusRequest;
import com.thiru.wealthlens.taxplanning.workspace.DraftView;
import com.thiru.wealthlens.taxplanning.workspace.RevisionResponse;
import com.thiru.wealthlens.taxplanning.workspace.WorkspaceResponse;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Workspaces and drafts (TS T8), with {@code ETag} and {@code If-Match} on a save ({@code DR 11} F5). */
@RequiredArgsConstructor
@RequestMapping("/tax/user/{email}")
@RestController
public class DraftController {

    private static final Pattern REVISION = Pattern.compile("^\"rev-(\\d{1,9})\"$");

    private final DraftService draftService;

    @GetMapping("/profiles/{profileId}/workspaces/{taxYear}")
    public WorkspaceResponse workspace(@PathVariable String email, @PathVariable String profileId, @PathVariable int taxYear) {
        return WorkspaceResponse.of(draftService.workspace(email, profileId, taxYear));
    }

    @GetMapping("/profiles/{profileId}/workspaces/{taxYear}/drafts")
    public List<DraftView> drafts(@PathVariable String email, @PathVariable String profileId, @PathVariable int taxYear) {
        return draftService.drafts(email, profileId, taxYear);
    }

    @PostMapping("/profiles/{profileId}/workspaces/{taxYear}/drafts")
    public ResponseEntity<DraftView> create(@PathVariable String email, @PathVariable String profileId,
            @PathVariable int taxYear, @RequestBody Map<String, String> body) {
        return tagged(draftService.createDraft(email, profileId, taxYear, body.get("label")));
    }

    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<DraftView> draft(@PathVariable String email, @PathVariable String draftId) {
        return tagged(draftService.draft(email, draftId));
    }

    @PatchMapping("/drafts/{draftId}")
    public ResponseEntity<?> patch(@PathVariable String email, @PathVariable String draftId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody JsonNode patch) {
        try {
            return tagged(draftService.patch(email, draftId, revisionIn(ifMatch), patch));
        } catch (DraftConflictException e) {
            return conflict(e.conflict());
        } catch (DataIntegrityViolationException e) {
            return conflict(draftService.conflictAfterRace(email, draftId, revisionIn(ifMatch), patch));
        }
    }

    @PostMapping("/drafts/{draftId}/status")
    public ResponseEntity<DraftView> status(@PathVariable String email, @PathVariable String draftId,
            @RequestBody DraftStatusRequest request) {
        return tagged(draftService.changeStatus(email, draftId, request));
    }

    @PostMapping("/drafts/{draftId}/primary")
    public WorkspaceResponse primary(@PathVariable String email, @PathVariable String draftId) {
        return WorkspaceResponse.of(draftService.makePrimary(email, draftId));
    }

    @GetMapping("/drafts/{draftId}/revisions")
    public List<RevisionResponse> revisions(@PathVariable String email, @PathVariable String draftId) {
        return draftService.revisions(email, draftId).stream().map(RevisionResponse::of).toList();
    }

    @GetMapping("/drafts/{draftId}/revisions/{number}")
    public JsonNode inputsAt(@PathVariable String email, @PathVariable String draftId, @PathVariable int number) {
        return draftService.inputsAt(email, draftId, number);
    }

    private static ResponseEntity<DraftConflictException.DraftConflict> conflict(DraftConflictException.DraftConflict conflict) {
        return ResponseEntity.status(HttpStatus.CONFLICT).eTag(tag(conflict.current())).body(conflict);
    }

    private static ResponseEntity<DraftView> tagged(DraftView view) {
        return ResponseEntity.ok().eTag(tag(view)).body(view);
    }

    private static String tag(DraftView view) {
        return "\"rev-" + view.revision() + "\"";
    }

    private static int revisionIn(String ifMatch) {
        Matcher match = ifMatch == null ? REVISION.matcher("") : REVISION.matcher(ifMatch);
        if (!match.matches()) {
            throw new BadRequestException("A save needs an If-Match header holding the revision it was made against");
        }
        return Integer.parseInt(match.group(1));
    }
}
