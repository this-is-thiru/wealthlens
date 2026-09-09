package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.dto.response.ChargeBreakdownResponse;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeReconciliationResponse;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.service.ChargeReconciliationService;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a user was actually charged.
 *
 * <p>Distinct from {@code /user-broker-charges}, which reads the superseded implementation's
 * documents. Both are live during Phase A on purpose: the old path still drives profit and loss,
 * and this one lets the two be compared before anything is cut over.
 */
@RequiredArgsConstructor
@RequestMapping("/user-charges/user/{email}")
@RestController
public class UserChargesController {

    private final UserChargeService userChargeService;
    private final ChargeReconciliationService chargeReconciliationService;

    /**
     * Charge history, newest first, optionally narrowed by date range and asset type.
     *
     * <p>Both date bounds or neither: a half-open range is rejected rather than interpreted,
     * because guessing which end was meant returns a short list that looks like missing charges.
     */
    @GetMapping
    public List<UserChargeEntity> history(
            @PathVariable String email,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to,
            @RequestParam(required = false) AssetType assetType) {
        return userChargeService.findHistory(email, from, to, assetType);
    }

    /** The contract note for one trade, line by line. */
    @GetMapping("/transaction/{transactionId}")
    public ChargeBreakdownResponse contractNote(@PathVariable String email, @PathVariable String transactionId) {
        return ChargeBreakdownResponse.from(userChargeService.findForTransaction(email, transactionId));
    }

    /**
     * Trades whose charges could not be fully assessed — no rate card for the period, no instrument
     * profile, or a provisional computation.
     *
     * <p>Worth an endpoint rather than a log line. Backfilling several years crosses periods with no
     * card on file, and a warning that scrolls away leaves a portfolio quietly under-costed.
     */
    @GetMapping("/gaps")
    public List<UserChargeEntity> gaps(@PathVariable String email) {
        return userChargeService.findGaps(email);
    }

    /**
     * What the engine computed against what the user entered, per trade, with the difference.
     *
     * <p>The reason Phase B exists. Shadow recording accumulates computed charges beside the
     * user-entered ones and changes nothing; this is where the two are compared, and the deltas are
     * what has to be understood before the computed total is allowed near cost basis.
     *
     * <p>Rows the engine could not price, and rows whose transaction is gone, are listed with a note
     * and left out of the totals. Subtracting either produces a figure that reads as a defect and is
     * not one.
     */
    @GetMapping("/reconciliation")
    public ChargeReconciliationResponse reconciliation(@PathVariable String email) {
        return chargeReconciliationService.reconcile(email);
    }
}
