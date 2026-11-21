package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.AccountErasureReceipt;
import com.thiru.wealthlens.shared.privacy.AccountErasureService;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Erasure of a whole account (Digital Personal Data Protection Act, 2023 s.12(3); W-237d). The user asks and keeps the
 * receipt the request returns: from then on the account cannot sign in, so a later read is the owner's, as a
 * {@code SUPER_USER} on the same path. After a restore from a snapshot, the owner applies again the erasures the snapshot
 * predates (W-237f).
 */
@MeApi
@RequestMapping("/erasure")
@RestController
@RequiredArgsConstructor
public class AccountErasureController {

    private final AccountErasureService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountErasureReceipt request(@CallerEmail String email) {
        return service.request(email);
    }

    @GetMapping
    public AccountErasureReceipt receipt(@CallerEmail String email) {
        return service.receipt(email);
    }
}
