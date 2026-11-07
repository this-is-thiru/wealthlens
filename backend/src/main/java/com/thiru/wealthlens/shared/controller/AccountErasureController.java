package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.AccountErasureReceipt;
import com.thiru.wealthlens.shared.privacy.AccountErasureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Erasure of a whole account (Digital Personal Data Protection Act, 2023 s.12(3); W-237d). The user asks and keeps the
 * receipt the request returns: from then on the account cannot sign in, so a later read is the owner's, as a
 * {@code SUPER_USER} on the same path.
 */
@RestController
@RequestMapping("/privacy/erasure")
@RequiredArgsConstructor
public class AccountErasureController {

    private final AccountErasureService service;

    @PostMapping("/user/{email}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountErasureReceipt request(@PathVariable String email) {
        return service.request(email);
    }

    @GetMapping("/user/{email}")
    public AccountErasureReceipt receipt(@PathVariable String email) {
        return service.receipt(email);
    }
}
