package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.BreachNoticeService;
import com.thiru.wealthlens.shared.privacy.UserBreachNotice;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The breach notices sent to the signed-in caller (split from {@code BreachNoticeController}, D-855 C6a). */
@RequiredArgsConstructor
@MeApi
@RestController
public class UserBreachNoticeController {

    private final BreachNoticeService service;

    @GetMapping("/breach-notices")
    public List<UserBreachNotice> noticesFor(@CallerEmail String email) {
        return service.noticesFor(email);
    }
}
