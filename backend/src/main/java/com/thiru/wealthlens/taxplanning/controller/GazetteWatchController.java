package com.thiru.wealthlens.taxplanning.controller;

import com.thiru.wealthlens.shared.web.AdminApi;
import com.thiru.wealthlens.taxplanning.gazette.GazetteCursorRequest;
import com.thiru.wealthlens.taxplanning.gazette.GazetteCursorResponse;
import com.thiru.wealthlens.taxplanning.gazette.GazetteLeadResponse;
import com.thiru.wealthlens.taxplanning.gazette.GazetteWatchService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The Gazette watch's leads, and where it starts — the owner's, so {@code SUPER_USER} only (W-236). */
@AdminApi
@RestController
@RequiredArgsConstructor
public class GazetteWatchController {

    private final GazetteWatchService gazetteWatch;

    /** Every lead, newest first. */
    @GetMapping("/gazette-leads")
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<List<GazetteLeadResponse>> leads() {
        return ResponseEntity.ok(gazetteWatch.leads().stream().map(GazetteLeadResponse::from).toList());
    }

    /** Where the watch starts: it reads the numbers after {@code lastNumber} in {@code year}'s folder. */
    @PutMapping("/gazette-cursor")
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<GazetteCursorResponse> seedCursor(@RequestBody GazetteCursorRequest request, Principal principal) {
        return ResponseEntity.ok(GazetteCursorResponse.from(gazetteWatch.seed(request, principal.getName())));
    }
}
