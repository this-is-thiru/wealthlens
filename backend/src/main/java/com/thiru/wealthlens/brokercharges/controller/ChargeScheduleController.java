package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.dto.response.ChargeScheduleDrift;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeSeedReport;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.service.ChargeCatalogueService;
import com.thiru.wealthlens.brokercharges.service.ChargeScheduleService;
import com.thiru.wealthlens.brokercharges.service.ChargeSeederService;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * {@code GET /charge-schedules/drift} is here for the same reason: it answers a question about
 * these cards, and the answer is only useful next to the list of them. {@code POST /charges/seed}
 * sits here too, under the other prefix, because it is the operation that creates them —
 * {@code ChargeAccountController} already carries the same split for the AMC cycle.
 */
@RequiredArgsConstructor
@RestController
public class ChargeScheduleController {

    private final ChargeScheduleService chargeScheduleService;
    private final ChargeCatalogueService chargeCatalogueService;
    private final ChargeSeederService chargeSeederService;

    /**
     * Publishes a card, superseding the incumbent for the same scope in the same transaction.
     *
     * <p>Restricted to a super user, as the tax-planning policy endpoints are. A rate card decides
     * what every user of the application is charged, and being authenticated is not the same as
     * being entitled to reprice other people's trades.
     */
    @PreAuthorize("hasRole('SUPER_USER')")
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
    @PreAuthorize("hasRole('SUPER_USER')")
    @PatchMapping("/charge-schedules/{scheduleCode}/close")
    public ChargeScheduleEntity close(@PathVariable String scheduleCode,
                                      @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return chargeScheduleService.close(scheduleCode, endDate);
    }

    /**
     * Applies the shipped catalogue, rate cards and scheme profiles to this database.
     *
     * <p>Deliberately an operation rather than a startup side effect. Seeding writes the rate cards
     * every user is charged against, and doing that automatically on boot meant it happened in
     * production without anyone asking, without anyone knowing when, and — because there is no
     * security context during startup — without a single seeded document able to say who wrote it.
     * Now it is a call somebody makes, and every document it writes carries their name (ADR-27).
     *
     * <p>Idempotent. A second run writes nothing and says so, so it is safe to make part of a
     * deployment checklist rather than something to be careful about.
     *
     * @param principal the caller, recorded on every document this run writes
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/charges/seed")
    public ChargeSeedReport seed(Principal principal) {
        return chargeSeederService.seed(principal.getName());
    }

    /**
     * Where the shipped rate cards and the database disagree.
     *
     * <p>Seeding is idempotent by {@code scheduleCode}, so a card already on file is never
     * overwritten by the file that ships beside it. Without this endpoint that divergence is
     * invisible: the repository says one thing, production charges another, and nothing reports it.
     *
     * <p>It does not say which side is right. A rate corrected in production through {@code POST
     * /charge-schedules} shows here, and so does a card edited in the repository that no deployment
     * has applied — opposite problems needing opposite fixes, which is why it takes a human.
     *
     * <p>Restricted to a super user: it discloses the full pricing of every broker.
     */
    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping("/charge-schedules/drift")
    public List<ChargeScheduleDrift> drift() {
        return chargeSeederService.findDrift();
    }

    @GetMapping("/charge-catalogue")
    public List<ChargeCatalogueEntity> catalogue() {
        return chargeCatalogueService.findActive();
    }
}
