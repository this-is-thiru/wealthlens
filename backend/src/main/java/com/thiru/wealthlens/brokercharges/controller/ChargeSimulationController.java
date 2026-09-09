package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.dto.request.ChargeSimulationRequest;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeBackfillReport;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeBreakdownResponse;
import com.thiru.wealthlens.brokercharges.service.ChargeBackfillService;
import com.thiru.wealthlens.brokercharges.service.ChargeSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prices a trade and records nothing — and, at {@code /charges/backfill}, prices a history and
 * records all of it.
 *
 * <p>A POST because the request is a document, not a set of query parameters — but it is a read,
 * and the service behind it holds no repository, so no route through this controller can write.
 *
 * <p>The endpoint earns its place twice over: it makes the whole engine exercisable from the API
 * collection without a portfolio to mutate, and it gives the interface a "what will this cost?"
 * answer that today can only be had by placing the trade.
 *
 * <p>The backfill below is the one route through this controller that <em>does</em> write, which is
 * why it is annotated and documented as sharply as it is.
 */
@RequiredArgsConstructor
@RequestMapping("/charges")
@RestController
public class ChargeSimulationController {

    private final ChargeSimulationService chargeSimulationService;
    private final ChargeBackfillService chargeBackfillService;

    @PostMapping("/simulate")
    public ChargeBreakdownResponse simulate(@RequestBody ChargeSimulationRequest request) {
        return chargeSimulationService.simulate(request);
    }

    /**
     * Prices one user's existing transactions and records the result.
     *
     * <p>Shadow recording only fires on trades that flow through the live path after the flag is
     * turned on, so a user's history carries no computed charges and
     * {@code GET /user-charges/user/{email}/reconciliation} has nothing to compare. This is what
     * gives that report its data — and the entered figures it reconciles against are ones the user
     * actually typed, which no freshly driven test trade can offer.
     *
     * <p><b>It writes.</b> One {@code user_charges} row per priced transaction, and nothing else:
     * no cost basis, no profit and loss figure and no transaction document is touched. Safe to
     * re-run — a row is keyed on {@code {email, transactionId}} and replaced rather than appended —
     * and reversible by dropping the rows it wrote.
     *
     * <p>Restricted to a super user. It is an operational tool that reprices somebody's entire
     * trading history, and the volume of writing it does should be somebody's decision rather than
     * a side effect of a page load.
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/backfill/user/{email}")
    public ChargeBackfillReport backfill(@PathVariable String email) {
        return chargeBackfillService.backfill(email);
    }
}
