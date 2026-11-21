package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.service.ChargeAccountService;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's broker accounts, registered for AMC and account-opening charges. The AMC cycle that bills them runs against every account
 * of a frequency at once, so it is an admin's, in {@code AmcCycleController} (D-855 C6a).
 */
@RequiredArgsConstructor
@MeApi
@RequestMapping("/charge-accounts")
@RestController
public class ChargeAccountController {

    private final ChargeAccountService chargeAccountService;

    /** Registers a demat account for AMC and account-opening charges. */
    @PostMapping
    public ChargeAccountEntity register(@CallerEmail String email, @RequestBody ChargeAccountEntity account) {
        account.setEmail(email);
        return chargeAccountService.register(account);
    }

    @GetMapping
    public List<ChargeAccountEntity> accounts(@CallerEmail String email) {
        return chargeAccountService.findByEmail(email);
    }
}
