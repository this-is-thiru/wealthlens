package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.AccountErasureReceipt;
import com.thiru.wealthlens.shared.privacy.AccountErasureService;
import com.thiru.wealthlens.shared.privacy.ErasureReapplication;
import com.thiru.wealthlens.shared.privacy.ReappliedErasures;
import com.thiru.wealthlens.shared.web.AdminApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Erasure on the owner's side: re-applying erasures, and a held user's receipt, which that user can no longer read (D-855 C3a, C6a). */
@RequiredArgsConstructor
@AdminApi
@RequestMapping("/erasure")
@RestController
public class AccountErasureAdminController {

    private final AccountErasureService service;

    @PostMapping("/reapply")
    @PreAuthorize("hasRole('SUPER_USER')")
    public ReappliedErasures reapply(@Valid @RequestBody ErasureReapplication reapplication) {
        return service.reapply(reapplication);
    }

    /** A held user's receipt: their own token stops working at the request, so the owner reads it here. */
    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping("/users/{email}")
    public AccountErasureReceipt receipt(@PathVariable String email) {
        return service.receipt(email);
    }
}
