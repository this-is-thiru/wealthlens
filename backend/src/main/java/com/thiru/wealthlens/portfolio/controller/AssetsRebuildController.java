package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.AssetsRebuildReport;
import com.thiru.wealthlens.portfolio.service.AssetsRebuild;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One user's lots as stored against a replay of their transactions (W-283a2, D-582). Read-only for now: it writes
 * nothing, and the write, with a {@code dryRun} flag defaulting to true, is W-283a3. A {@code SUPER_USER} asks.
 */
@RequiredArgsConstructor
@RestController
public class AssetsRebuildController {

    private final AssetsRebuild rebuild;

    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/portfolio/user/{email}/assets/rebuild")
    public AssetsRebuildReport rebuild(@PathVariable String email) {
        return rebuild.rebuild(UserMail.from(email));
    }
}
