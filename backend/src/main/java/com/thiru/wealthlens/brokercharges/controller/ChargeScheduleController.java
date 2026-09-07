package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.service.ChargeCatalogueService;
import com.thiru.wealthlens.brokercharges.service.ChargeScheduleService;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rate-card administration.
 *
 * <p>Every route here is authenticated (see {@code AuthConfig}). A rate card decides what every
 * user is charged, so an open write endpoint would be a way to alter everybody's cost basis, and an
 * open read endpoint publishes a broker's negotiated pricing.
 *
 * <h2>Two deviations from the tech spec's table</h2>
 * Schedules are addressed by {@code scheduleCode}, not by Mongo id. The code is the natural key —
 * it is what the seed files, the validator and every stored charge row already carry, and it is the
 * only one of the two a human can quote.
 *
 * <p>{@code GET /charge-catalogue} lives here rather than in a controller of its own. It is the
 * registry a rate card's codes are validated against, so it is read by whoever is authoring one.
 */
@RequiredArgsConstructor
@RestController
public class ChargeScheduleController {

    private final ChargeScheduleService chargeScheduleService;
    private final ChargeCatalogueService chargeCatalogueService;

    /** Publishes a card, superseding the incumbent for the same scope in the same transaction. */
    @PostMapping("/charge-schedules")
    public ChargeScheduleEntity publish(@RequestBody ChargeScheduleEntity schedule) {
        return chargeScheduleService.publish(schedule);
    }

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

    /** Withdraws a card without replacing it. Past dates still resolve against it. */
    @PatchMapping("/charge-schedules/{scheduleCode}/close")
    public ChargeScheduleEntity close(@PathVariable String scheduleCode,
                                      @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return chargeScheduleService.close(scheduleCode, endDate);
    }

    @GetMapping("/charge-catalogue")
    public List<ChargeCatalogueEntity> catalogue() {
        return chargeCatalogueService.findActive();
    }
}
