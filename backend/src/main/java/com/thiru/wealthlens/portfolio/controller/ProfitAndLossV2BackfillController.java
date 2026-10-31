package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.V2BackfillReport;
import com.thiru.wealthlens.portfolio.migration.ProfitAndLossV2Backfill;
import java.security.Principal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adds the V2 sales' profit, brokerage and misc, recorded before W-169, into the P&L split (W-172, D-373).
 *
 * <p>Asked for, never automatic (D-280): a {@code SUPER_USER} calls it, a dry run comes first, and every document
 * changed records who called. One-time: removed once its run is logged for every database (W-173).
 */
@RequiredArgsConstructor
@RestController
public class ProfitAndLossV2BackfillController {

    private final ProfitAndLossV2Backfill backfill;

    /**
     * What the backfill finds and, with {@code dryRun=false}, adds.
     *
     * @param recordedBefore the deploy, in the server's time ({@code yyyy-MM-dd'T'HH:mm:ss}): only rows written
     *     before it are taken
     * @param principal the caller, recorded on every document this run changes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/profit-and-loss/v2-figures/backfill")
    public V2BackfillReport backfill(@RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedBefore,
            Principal principal) {
        return backfill.backfill(dryRun, recordedBefore, principal.getName());
    }
}
