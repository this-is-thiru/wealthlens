package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.AssetsRebuildReport;
import com.thiru.wealthlens.portfolio.service.AssetsRebuild;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One user's lots as stored against a replay of their transactions and, with {@code dryRun=false}, replaced by it
 * (W-283a2, D-582; W-283a3, D-586). A {@code SUPER_USER} asks; {@code dryRun} defaults to true, so the call that
 * writes is the one that says so (as D-284, D-575).
 */
@RequiredArgsConstructor
@RestController
public class AssetsRebuildController {

    private final AssetsRebuild rebuild;

    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/portfolio/user/{email}/assets/rebuild")
    public AssetsRebuildReport rebuild(@PathVariable String email,
                                       @RequestParam(defaultValue = "true") boolean dryRun) {
        return rebuild.rebuild(UserMail.from(email), dryRun);
    }
}
