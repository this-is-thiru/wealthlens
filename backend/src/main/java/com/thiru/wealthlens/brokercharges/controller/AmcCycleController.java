package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.service.AmcChargeService;
import com.thiru.wealthlens.shared.web.AdminApi;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AMC cycle, run against every account of a frequency at once: an admin's operation (split from {@code ChargeAccountController},
 * D-855 C6a). One call bills real money across every user.
 *
 * <p>{@code POST /api/v1/admin/charges/amc/impose} does not replace {@code /broker-charges/amc/impose}. The old endpoint bills from
 * {@code AssetManagementDetails} and stays live until Phase C; this one bills from {@code charge_accounts} through the engine. Running
 * both against the same period would charge twice, which is why the cutover deletes rather than redirects.
 */
@RequiredArgsConstructor
@AdminApi
@RestController
public class AmcCycleController {

    private final AmcChargeService amcChargeService;

    /**
     * What an AMC run would bill, without billing it.
     *
     * <p>There is no scheduler: the cycle runs when somebody calls it. That makes silent
     * non-billing the failure mode — months can pass with nobody billed and nothing saying so — and
     * this is the endpoint that makes the gap visible. Safe to poll: it writes nothing and moves no
     * billing watermark.
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping("/charge-accounts/due")
    public List<ChargeAccountEntity> amcDue(
            @RequestParam AmcChargeFrequency frequency,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate billedThrough) {
        return amcChargeService.findDue(frequency, billedThrough);
    }

    /**
     * Bills every account of the given frequency not yet covered through the given date.
     *
     * <p>Restricted to a super user: one call bills every account of a frequency, across every
     * user, and the charges it writes are real money against real cost bases.
     *
     * @return the accounts actually billed, so a caller can see what a run did rather than assume
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/amc/impose")
    public List<ChargeAccountEntity> imposeAmc(
            @RequestParam AmcChargeFrequency frequency,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate billedThrough) {
        return amcChargeService.runCycle(frequency, billedThrough);
    }
}
