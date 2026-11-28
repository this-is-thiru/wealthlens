package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.InstrumentAliasRegistration;
import com.thiru.wealthlens.portfolio.dto.InstrumentAliasResponse;
import com.thiru.wealthlens.portfolio.dto.InstrumentAliasesRequest;
import com.thiru.wealthlens.portfolio.service.InstrumentAliasService;
import com.thiru.wealthlens.shared.web.AdminApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The instrument registry's aliases, kept by the owner (W-440b, D-1055, C15a): registered from the owner's own broker files, never
 * seeded, since the list of instruments is the owner's holdings. Runbook §1.3 gives the call and its checks.
 */
@RequiredArgsConstructor
@AdminApi
@RequestMapping("/instrument-aliases")
@RestController
public class InstrumentAliasAdminController {

    private final InstrumentAliasService instrumentAliasService;

    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping
    public InstrumentAliasRegistration register(@Valid @RequestBody InstrumentAliasesRequest request) {
        return instrumentAliasService.register(request.aliases());
    }

    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping
    public PagedModel<InstrumentAliasResponse> aliases(Pageable pageable) {
        return new PagedModel<>(instrumentAliasService.aliases(pageable));
    }
}
