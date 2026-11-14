package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.BonusAllotmentCorrectionReport;
import com.thiru.wealthlens.portfolio.dto.BonusAllotmentDate;
import com.thiru.wealthlens.portfolio.migration.BonusAllotmentDateCorrection;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Re-dates the bonus lots processed before W-088k4a from the ex-date to the allotment date the owner supplies
 * (W-088k4b, D-308).
 *
 * <p>Asked for, never automatic (D-280): a {@code SUPER_USER} calls it, a dry run comes first, and every document
 * written records who called. One-time: removed once its run is logged for every database (W-150).
 */
@RequiredArgsConstructor
@RestController
public class BonusAllotmentDateCorrectionController {

    private final BonusAllotmentDateCorrection correction;

    /**
     * What the correction finds for each bonus action given and, with {@code dryRun=false}, re-dates.
     *
     * @param principal the caller, recorded on every document this run writes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/corporate-actions/bonus/allotment-dates/correct")
    public BonusAllotmentCorrectionReport correct(@RequestBody List<BonusAllotmentDate> entries,
            @RequestParam(defaultValue = "true") boolean dryRun, Principal principal) {
        return correction.correct(entries, dryRun, principal.getName());
    }
}
