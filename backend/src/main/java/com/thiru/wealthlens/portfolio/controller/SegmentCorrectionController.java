package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.SegmentCorrectionReport;
import com.thiru.wealthlens.portfolio.migration.SegmentCorrection;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restores the segment that transactions and outcome rows stored before W-292 / W-295 lost, from what stored data
 * records of it (W-297, D-599).
 *
 * <p>Asked for, never automatic (D-280): a {@code SUPER_USER} calls it, a dry run comes first, and every document
 * written records who called. One-time: removed once its run is logged for every database (W-299).
 */
@RequiredArgsConstructor
@RestController
public class SegmentCorrectionController {

    private final SegmentCorrection correction;

    /**
     * What the correction finds and, with {@code dryRun=false}, restores.
     *
     * @param principal the caller, recorded on every document this run writes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/transactions/segment/correct")
    public SegmentCorrectionReport correct(@RequestParam(defaultValue = "true") boolean dryRun, Principal principal) {
        return correction.correct(dryRun, principal.getName());
    }
}
