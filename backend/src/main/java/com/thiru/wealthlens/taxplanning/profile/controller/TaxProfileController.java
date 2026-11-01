package com.thiru.wealthlens.taxplanning.profile.controller;

import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRequest;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Taxpayer profiles (TS T8's base, D-046 B3). {@code AuthFilter} refuses a caller whose token is not the path's email ({@code DR 14} O2). */
@RequiredArgsConstructor
@RequestMapping("/tax/user/{email}/profiles")
@RestController
public class TaxProfileController {

    private final TaxProfileService service;

    @PostMapping
    public TaxpayerProfileResponse create(@PathVariable String email, @RequestBody TaxpayerProfileRequest request) {
        return TaxpayerProfileResponse.of(service.create(email, request));
    }

    @GetMapping
    public List<TaxpayerProfileResponse> all(@PathVariable String email) {
        return service.all(email).stream().map(TaxpayerProfileResponse::of).toList();
    }

    @GetMapping("/{profileId}")
    public TaxpayerProfileResponse one(@PathVariable String email, @PathVariable String profileId) {
        return TaxpayerProfileResponse.of(service.owned(email, profileId));
    }

    @PutMapping("/{profileId}")
    public TaxpayerProfileResponse replace(@PathVariable String email, @PathVariable String profileId,
            @RequestBody TaxpayerProfileRequest request) {
        return TaxpayerProfileResponse.of(service.replace(email, profileId, request));
    }
}
