package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.engine.ChargeScheduleResolver;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the shipped charge catalogue and rate cards at startup.
 *
 * <h2>It fails fast, unlike the seeder it is modelled on</h2>
 * {@code PolicySeederService} logs and continues when a file will not load. That is the wrong
 * behaviour here: an application that starts with a malformed or missing rate card goes on to price
 * real trades from whatever did load, and the resulting charges look exactly like correct ones.
 * A bad card stops startup (AC-9).
 *
 * <h2>Order matters</h2>
 * The catalogue is written before the cards, because the validator rejects any rule code absent from
 * it. Seeding a card first would fail against an empty catalogue.
 *
 * <h2>Idempotent by code</h2>
 * The seeder runs on every startup. A catalogue entry already present is left alone, and so is a
 * schedule whose {@code scheduleCode} is on file — including one an operator has since edited, which
 * must not be silently overwritten by the shipped version.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ChargeSeederService {

    private static final String CATALOGUE = "classpath:data/charges/charge-catalogue.json";
    private static final String SCHEDULES = "classpath*:data/charges/*.json";
    private static final String CATALOGUE_FILE = "charge-catalogue.json";

    private final ChargeCatalogueRepository chargeCatalogueRepository;
    private final ChargeScheduleRepository chargeScheduleRepository;
    private final ChargeScheduleValidator chargeScheduleValidator;
    private final ChargeScheduleResolver chargeScheduleResolver;

    /**
     * Injected rather than constructed, so the failure path when the classpath cannot be listed is
     * reachable from a test. Spring supplies the application context, which is one of these.
     */
    private final ResourcePatternResolver resourceResolver;

    /**
     * An omitted boolean means false, which is what the entity defaults to and what a rate card
     * means by leaving {@code perLot} or {@code appliesToCorporateActions} out. Jackson 3 turned
     * {@code FAIL_ON_NULL_FOR_PRIMITIVES} on by default, so without this a card is rejected for not
     * restating every flag it does not use.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @PostConstruct
    public void seed() {
        log.info("Seeding charge catalogue and rate cards");
        seedCatalogue();
        seedSchedules();
        // Startup may already have resolved for a scope; the newly seeded cards must be visible.
        chargeScheduleResolver.evictAll();
        log.info("Charge seeding completed");
    }

    private void seedCatalogue() {
        for (ChargeCatalogueEntity entry : read(resource(CATALOGUE), ChargeCatalogueEntity[].class)) {
            if (chargeCatalogueRepository.existsByCode(entry.getCode())) {
                continue;
            }
            chargeCatalogueRepository.save(entry);
            log.info("Seeded charge code {}", entry.getCode());
        }
    }

    private void seedSchedules() {
        for (Resource file : scheduleFiles()) {
            ChargeScheduleEntity schedule = read(file, ChargeScheduleEntity.class);

            if (chargeScheduleRepository.findByScheduleCode(schedule.getScheduleCode()).isPresent()) {
                // Possibly edited since; the shipped version must not overwrite it.
                continue;
            }

            // Validated before persisting, so a typo stops the build rather than a quarter of trades.
            // Rewrapped: BadRequestException maps to HTTP 400, and a rate card this application
            // ships is not a bad request from anyone — it is bad data of our own, and the message
            // has to name the file whoever fixes it will open.
            try {
                chargeScheduleValidator.validate(schedule);
            } catch (BadRequestException e) {
                throw new IllegalStateException("Shipped charge rate card " + file.getFilename()
                        + " is invalid: " + e.getMessage(), e);
            }
            chargeScheduleRepository.save(schedule);
            log.info("Seeded charge schedule {} for {}", schedule.getScheduleCode(), schedule.getBrokerName());
        }
    }

    /** Sorted, so seeding order is the same on every machine and a failure reproduces. */
    private List<Resource> scheduleFiles() {
        try {
            return Arrays.stream(resourceResolver.getResources(SCHEDULES))
                    .filter(resource -> !CATALOGUE_FILE.equals(resource.getFilename()))
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list the shipped charge rate cards", e);
        }
    }

    private Resource resource(String location) {
        return resourceResolver.getResource(location);
    }

    <T> T read(Resource file, Class<T> type) {
        try (InputStream stream = file.getInputStream()) {
            return objectMapper.readValue(stream, type);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not read the charge seed file " + file.getFilename() + ": " + e.getMessage(), e);
        }
    }
}
