package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.StoredTermRemovalReport;
import com.thiru.wealthlens.portfolio.migration.TradeOutcomeStoredTermRemoval;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Removes the term {@code portfolio} once stored on {@code trade_outcomes} rows (W-160c, D-365).
 *
 * <p>Asked for, never automatic (D-280): a {@code SUPER_USER} calls it, a dry run comes first, and every row changed
 * records who called. One-time: removed once its run is logged for every database (W-168).
 */
@RequiredArgsConstructor
@RestController
public class TradeOutcomeStoredTermController {

    private final TradeOutcomeStoredTermRemoval removal;

    /**
     * What the removal finds and, with {@code dryRun=false}, removes.
     *
     * @param principal the caller, recorded on every row this run changes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/trade-outcomes/stored-terms/remove")
    public StoredTermRemovalReport remove(@RequestParam(defaultValue = "true") boolean dryRun, Principal principal) {
        return removal.removeStoredTerms(dryRun, principal.getName());
    }
}
