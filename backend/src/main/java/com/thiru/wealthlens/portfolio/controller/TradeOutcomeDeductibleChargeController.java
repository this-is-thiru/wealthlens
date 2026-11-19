package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.DeductibleChargeRemovalReport;
import com.thiru.wealthlens.portfolio.migration.TradeOutcomeDeductibleChargeRemoval;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Removes the deductible charges once stored on {@code trade_outcomes} rows (W-162b, D-377).
 *
 * <p>Asked for, never automatic (D-280): a {@code SUPER_USER} calls it, a dry run comes first, and every row changed
 * records who called. One-time: removed once its run is logged for every database (W-174).
 */
@RequiredArgsConstructor
@RestController
public class TradeOutcomeDeductibleChargeController {

    private final TradeOutcomeDeductibleChargeRemoval removal;

    /**
     * What the removal finds and, with {@code dryRun=false}, removes.
     *
     * @param principal the caller, recorded on every row this run changes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/trade-outcomes/deductible-charges/remove")
    public DeductibleChargeRemovalReport remove(@RequestParam(defaultValue = "true") boolean dryRun,
            Principal principal) {
        return removal.removeDeductibleCharges(dryRun, principal.getName());
    }
}
