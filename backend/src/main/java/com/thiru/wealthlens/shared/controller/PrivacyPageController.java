package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.PrivacyNotice;
import com.thiru.wealthlens.shared.privacy.PrivacySettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The privacy notice as a page, public, for a person with a browser and no client: no website or app exists apart from
 * this one, and DPDP Rules r.14(1) and r.9 ask the notice published on one (W-247 C1a). Built from the same settings as
 * {@code GET /privacy/rights}, so the page and the JSON cannot differ. A view, not a response body, so the JSON wrapper
 * leaves it alone.
 */
@Controller
@RequiredArgsConstructor
public class PrivacyPageController {

    private final PrivacySettings privacy;

    @GetMapping(PrivacyNotice.PAGE)
    public String notice(Model model) {
        model.addAttribute("notice", privacy.notice());
        return "privacy/notice";
    }
}
