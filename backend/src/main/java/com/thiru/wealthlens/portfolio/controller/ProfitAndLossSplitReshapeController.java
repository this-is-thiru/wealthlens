package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.SplitReshapeReport;
import com.thiru.wealthlens.portfolio.migration.ProfitAndLossSplitReshape;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moves the P&L's stored short / long pair into its split by asset type and segment (W-160d3, D-369).
 *
 * <p>Asked for, never automatic (D-280): a {@code SUPER_USER} calls it, a dry run comes first, and every document
 * changed records who called. One-time: removed once its run is logged for every database (W-170).
 */
@RequiredArgsConstructor
@RestController
public class ProfitAndLossSplitReshapeController {

    private final ProfitAndLossSplitReshape reshape;

    /**
     * What the reshape finds and, with {@code dryRun=false}, moves.
     *
     * @param principal the caller, recorded on every document this run changes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/profit-and-loss/split/reshape")
    public SplitReshapeReport reshape(@RequestParam(defaultValue = "true") boolean dryRun, Principal principal) {
        return reshape.reshape(dryRun, principal.getName());
    }
}
