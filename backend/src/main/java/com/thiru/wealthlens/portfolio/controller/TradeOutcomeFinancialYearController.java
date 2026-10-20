package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.FinancialYearRewriteReport;
import com.thiru.wealthlens.portfolio.migration.TradeOutcomeFinancialYearMigration;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rewrites the {@code FY2025-26}-form financial years the old rebuild left in {@code trade_outcomes} (W-086a, D-239)
 * — the one job left for it is prod's 163 rows (W-088u8c, D-285).
 *
 * <p>Asked for, never automatic (D-280): a {@code SUPER_USER} calls it, a dry run comes first, and every row rewritten
 * records who called. One-time: removed once its prod run is logged (W-088u9d, YouTrack WL-32).
 */
@RequiredArgsConstructor
@RestController
public class TradeOutcomeFinancialYearController {

    private final TradeOutcomeFinancialYearMigration migration;

    /**
     * What the rewrite finds and, with {@code dryRun=false}, rewrites.
     *
     * @param principal the caller, recorded on every row this run rewrites
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/trade-outcomes/financial-years/rewrite")
    public FinancialYearRewriteReport rewrite(@RequestParam(defaultValue = "true") boolean dryRun, Principal principal) {
        return migration.rewriteFinancialYears(dryRun, principal.getName());
    }
}
