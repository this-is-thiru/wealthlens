package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.PrivacyNotice;
import com.thiru.wealthlens.shared.privacy.PrivacySettings;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The privacy notice, public so a user can read it before signing in or asking anything (W-237a; DPDP Rules r.14(1), r.9). */
@RestController
@RequestMapping("/privacy")
@RequiredArgsConstructor
public class PrivacyController {

    private final PrivacySettings privacy;

    @GetMapping("/rights")
    public PrivacyNotice rights() {
        return privacy.notice();
    }
}
