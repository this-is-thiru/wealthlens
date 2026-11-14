package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.TradeOutcomeRebuildReport;
import com.thiru.wealthlens.portfolio.migration.TransactionBasedTradeOutcomeMigration;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rebuilds {@code trade_outcomes} from raw transactions for every user who has none (W-088u8b, D-284).
 *
 * <p>Asked for, never automatic (D-280): it writes rows for every such user, so a {@code SUPER_USER} calls it, a dry
 * run comes first, and every row records who called. {@code dryRun} defaults to true, so the call that writes is the
 * one that says so.
 */
@RequiredArgsConstructor
@RestController
public class TradeOutcomeRebuildController {

    private final TransactionBasedTradeOutcomeMigration rebuild;

    /**
     * What the rebuild finds and, with {@code dryRun=false}, writes.
     *
     * @param principal the caller, recorded on every row this run writes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/trade-outcomes/rebuild")
    public TradeOutcomeRebuildReport rebuild(@RequestParam(defaultValue = "true") boolean dryRun, Principal principal) {
        return rebuild.rebuild(dryRun, principal.getName());
    }
}
