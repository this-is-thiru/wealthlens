package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.ProfitAndLossRebuildReport;
import com.thiru.wealthlens.portfolio.service.ProfitAndLossRebuild;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rebuilds one user's {@code profit_and_loss} from stored rows (W-283, D-575).
 *
 * <p>Asked for, never automatic: a {@code SUPER_USER} calls it after correcting the user's transactions, a dry run
 * comes first, and {@code dryRun} defaults to true so the call that writes is the one that says so (as D-284).
 */
@RequiredArgsConstructor
@RestController
public class ProfitAndLossRebuildController {

    private final ProfitAndLossRebuild rebuild;

    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/portfolio/user/{email}/profit-and-loss/rebuild")
    public ProfitAndLossRebuildReport rebuild(@PathVariable String email,
                                              @RequestParam(defaultValue = "true") boolean dryRun) {
        return rebuild.rebuild(UserMail.from(email), dryRun);
    }
}
