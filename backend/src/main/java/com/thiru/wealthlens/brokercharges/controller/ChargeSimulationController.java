package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.dto.request.ChargeSimulationRequest;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeBreakdownResponse;
import com.thiru.wealthlens.brokercharges.service.ChargeSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prices a trade and records nothing.
 *
 * <p>A POST because the request is a document, not a set of query parameters — but it is a read,
 * and the service behind it holds no repository, so no route through this controller can write.
 * That is asserted, not promised: {@code ChargeSimulationServiceTest} reads the service's fields
 * and fails if a repository ever appears among them.
 *
 * <p>The endpoint earns its place twice over: it makes the whole engine exercisable from the API
 * collection without a portfolio to mutate, and it gives the interface a "what will this cost?"
 * answer that today can only be had by placing the trade.
 *
 * <p>This controller once also carried {@code POST /charges/backfill}, which repriced a user's whole
 * history. It was removed with ADR-32: existing transactions keep their charges and are never
 * re-driven, so an endpoint whose only purpose is re-driving them could do nothing correct and
 * plenty that was wrong.
 */
@RequiredArgsConstructor
@RequestMapping("/charges")
@RestController
public class ChargeSimulationController {

    private final ChargeSimulationService chargeSimulationService;

    @PostMapping("/simulate")
    public ChargeBreakdownResponse simulate(@RequestBody ChargeSimulationRequest request) {
        return chargeSimulationService.simulate(request);
    }
}
