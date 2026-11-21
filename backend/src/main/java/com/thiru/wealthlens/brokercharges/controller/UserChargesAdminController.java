package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.dto.response.ChargeBreakdownResponse;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeReconciliationResponse;
import com.thiru.wealthlens.brokercharges.dto.response.UserChargeResponse;
import com.thiru.wealthlens.brokercharges.service.ChargeReconciliationService;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.shared.web.AdminApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One user's charges, read by the owner when something looks wrong (runbook §5.4, D-855 C3a): the gaps, a trade's contract note and
 * the reconciliation, as {@link UserChargesController} gives them to the user.
 */
@RequiredArgsConstructor
@AdminApi
@RequestMapping("/user-charges/users/{email}")
@RestController
public class UserChargesAdminController {

    private final UserChargeService userChargeService;
    private final ChargeReconciliationService chargeReconciliationService;

    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping("/transaction/{transactionId}")
    public ChargeBreakdownResponse contractNote(@PathVariable String email, @PathVariable String transactionId) {
        return ChargeBreakdownResponse.from(userChargeService.findForTransaction(email, transactionId));
    }

    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping("/gaps")
    public List<UserChargeResponse> gaps(@PathVariable String email) {
        return userChargeService.findGaps(email).stream().map(UserChargeResponse::from).toList();
    }

    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping("/reconciliation")
    public ChargeReconciliationResponse reconciliation(@PathVariable String email) {
        return chargeReconciliationService.reconcile(email);
    }
}
