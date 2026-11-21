package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.service.ChargeCatalogueService;
import com.thiru.wealthlens.brokercharges.service.ChargeScheduleService;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.web.MeApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rate cards and the charge catalogue, read by any signed-in user (D-855 C2a): a card decides what they are charged.
 * Publishing, closing, seeding and the drift check are an admin's, in {@code ChargeSchedulePublishingController} (D-855 C6a).
 *
 * <p>Schedules are addressed by {@code scheduleCode}, not by Mongo id. The code is the natural key — it is what the seed files,
 * the validator and every stored charge row already carry, and it is the only one of the two a human can quote.
 *
 * <p>{@code GET /api/v1/me/charges/charge-catalogue} lives here rather than in a controller of its own. It is the registry a rate
 * card's codes are validated against, so it is read by whoever is authoring one.
 */
@RequiredArgsConstructor
@MeApi
@RestController
public class ChargeScheduleController {

    private final ChargeScheduleService chargeScheduleService;
    private final ChargeCatalogueService chargeCatalogueService;

    @GetMapping("/charge-schedules/{scheduleCode}")
    public ChargeScheduleEntity findByCode(@PathVariable String scheduleCode) {
        return chargeScheduleService.findByCode(scheduleCode);
    }

    @GetMapping("/charge-schedules")
    public List<ChargeScheduleEntity> findByBroker(@RequestParam BrokerName broker) {
        return chargeScheduleService.findByBroker(broker);
    }

    /**
     * The cards whose rates nobody has checked against the broker's published page.
     *
     * <p>Every shipped card is one of these today, which is the point: the figures are placeholders
     * and AC-2 stays open until this list is empty. An endpoint makes that visible to whoever has to
     * do the checking, rather than leaving it in a document.
     */
    @GetMapping("/charge-schedules/unverified")
    public List<ChargeScheduleEntity> findUnverified() {
        return chargeScheduleService.findUnverified();
    }

    @GetMapping("/charge-catalogue")
    public List<ChargeCatalogueEntity> catalogue() {
        return chargeCatalogueService.findActive();
    }
}
