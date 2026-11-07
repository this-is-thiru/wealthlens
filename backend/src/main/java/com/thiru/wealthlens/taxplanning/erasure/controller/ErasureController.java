package com.thiru.wealthlens.taxplanning.erasure.controller;

import com.thiru.wealthlens.taxplanning.erasure.ErasureReceipt;
import com.thiru.wealthlens.taxplanning.erasure.ErasureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's request to erase one of their profiles (W-235 C3a). Accepted, not done: the erasure follows the hold the receipt names.
 * {@code AuthFilter} refuses a caller whose token is not the path's email.
 */
@RequiredArgsConstructor
@RestController
public class ErasureController {

    private final ErasureService service;

    @PostMapping("/tax/user/{email}/profiles/{profileId}/erasure")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ErasureReceipt request(@PathVariable String email, @PathVariable String profileId) {
        return service.request(email, profileId);
    }
}
