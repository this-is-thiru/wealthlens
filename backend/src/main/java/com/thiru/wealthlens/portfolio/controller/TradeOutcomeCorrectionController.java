package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.RebuildChargeCorrectionReport;
import com.thiru.wealthlens.portfolio.migration.RebuildBuyChargeCorrection;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Corrects the {@code trade_outcomes} rows an earlier startup rebuild wrote with a lot's buy charge counted again on
 * each later sell (W-088u7, D-276).
 *
 * <p>An operation rather than a startup side effect, for the reason {@code POST /charges/seed} is one: it rewrites
 * money in production, so somebody asks for it, a dry run comes first, and every row it writes carries their name.
 * {@code dryRun} defaults to true, so the call that writes is the one that says so.
 */
@RequiredArgsConstructor
@RestController
public class TradeOutcomeCorrectionController {

    private final RebuildBuyChargeCorrection rebuildBuyChargeCorrection;

    /**
     * What the correction finds and, with {@code dryRun=false}, writes.
     *
     * @param principal the caller, recorded on every row this run writes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/trade-outcomes/rebuild-charges/correct")
    public RebuildChargeCorrectionReport correct(@RequestParam(defaultValue = "true") boolean dryRun,
            Principal principal) {
        return rebuildBuyChargeCorrection.correct(dryRun, principal.getName());
    }
}
