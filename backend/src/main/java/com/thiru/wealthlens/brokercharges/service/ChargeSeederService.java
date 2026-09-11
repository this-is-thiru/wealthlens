package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.response.ChargeScheduleDrift;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeSeedReport;
import com.thiru.wealthlens.brokercharges.engine.ChargeInstrumentResolver;
import com.thiru.wealthlens.brokercharges.engine.ChargeScheduleResolver;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeInstrumentRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Loads the shipped charge catalogue and rate cards at startup.
 *
 * <h2>It runs when asked, not at startup</h2>
 * {@code POST /charges/seed} is the only caller. Seeding at {@code @PostConstruct} meant a
 * deployment wrote rate cards as a side effect of booting, in every environment, with no record of
 * who or when — there is no security context during startup, so not one document could name its
 * author. See ADR-27.
 *
 * <h2>It fails fast, unlike the seeder it is modelled on</h2>
 * {@code PolicySeederService} logs and continues when a file will not load. That is the wrong
 * behaviour here: an application that starts with a malformed or missing rate card goes on to price
 * real trades from whatever did load, and the resulting charges look exactly like correct ones.
 * A bad card stops startup (AC-9).
 *
 * <h2>Order matters</h2>
 * The catalogue is written before the cards, because the validator rejects any rule code absent from
 * it. Seeding a card first would fail against an empty catalogue. Instrument profiles come last:
 * they are what the mutual fund cards declare {@code requiresInstrumentProfile} against, and they
 * name catalogue codes of their own.
 *
 * <h2>Idempotent by code</h2>
 * The seeder runs on every startup. A catalogue entry already present is left alone, and so is a
 * schedule whose {@code scheduleCode} is on file — including one an operator has since edited, which
 * must not be silently overwritten by the shipped version. A profile carries no code, so its
 * identity is the scheme and the date its version took effect.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ChargeSeederService {

    private static final String CATALOGUE = "classpath:data/charges/charge-catalogue.json";
    private static final String SCHEDULES = "classpath*:data/charges/*.json";
    private static final String INSTRUMENTS = "classpath*:data/charges/instruments/*.json";
    private static final String CATALOGUE_FILE = "charge-catalogue.json";

    /** Written by MongoDB, absent from every shipped file, and therefore never drift. */
    private static final List<String> DATABASE_OWNED = List.of("id", "auditMetadata");

    private final ChargeCatalogueRepository chargeCatalogueRepository;
    private final ChargeScheduleRepository chargeScheduleRepository;
    private final ChargeInstrumentRepository chargeInstrumentRepository;
    private final ChargeScheduleValidator chargeScheduleValidator;
    private final ChargeScheduleResolver chargeScheduleResolver;
    private final ChargeInstrumentResolver chargeInstrumentResolver;

    /**
     * Injected rather than constructed, so the failure path when the classpath cannot be listed is
     * reachable from a test.
     *
     * <p>Declared as the {@code ApplicationContext} rather than as the {@code ResourcePatternResolver}
     * it is used as. Both work — the context is one — but the narrower type has more than one bean
     * answering to it once GridFS is on the classpath, so the injection resolves implicitly and every
     * IDE flags it. {@code ApplicationContext} is registered as a resolvable dependency and is
     * unambiguous by construction.
     */
    private final ApplicationContext resourceResolver;

    /**
     * An omitted boolean means false, which is what the entity defaults to and what a rate card
     * means by leaving {@code perLot} or {@code appliesToCorporateActions} out. Jackson 3 turned
     * {@code FAIL_ON_NULL_FOR_PRIMITIVES} on by default, so without this a card is rejected for not
     * restating every flag it does not use.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    /**
     * Applies the shipped catalogue, rate cards and scheme profiles, and says what it did.
     *
     * <p>Called by {@code POST /charges/seed} and by nothing else. It used to run at startup, which
     * meant a deployment wrote to the database as a side effect of booting — as nobody, since there
     * is no security context at {@code @PostConstruct}, so not one seeded document could say who
     * had written it. Doing it deliberately and as somebody is the whole point (ADR-27).
     *
     * <p>Safe to run repeatedly: everything here is idempotent, and a second run reports that it
     * wrote nothing rather than doing anything twice.
     *
     * @param auditor recorded on the report. The documents themselves are stamped by Spring Data's
     *                auditing, which reads the authenticated caller through {@code SecurityAuditorAware}
     */
    @Transactional
    public ChargeSeedReport seed(String auditor) {
        log.info("Seeding charge catalogue, rate cards and scheme profiles, requested by {}", auditor);
        List<String> catalogue = seedCatalogue();
        List<String> schedulesCreated = new ArrayList<>();
        List<String> schedulesSkipped = new ArrayList<>();
        seedSchedules(schedulesCreated, schedulesSkipped);

        List<String> instrumentsCreated = new ArrayList<>();
        List<String> instrumentsSkipped = new ArrayList<>();
        seedInstruments(instrumentsCreated, instrumentsSkipped);

        // Anything already resolved for a scope must not outlive the cards this run wrote. Both
        // resolvers cache misses as well as hits, so neither would re-ask on its own.
        chargeScheduleResolver.evictAll();
        chargeInstrumentResolver.evictAll();

        ChargeSeedReport report = new ChargeSeedReport(auditor, catalogue, schedulesCreated,
                schedulesSkipped, instrumentsCreated, instrumentsSkipped, findDrift());
        log.info("Charge seeding completed: {} codes, {} cards, {} profiles written; {} drifted",
                catalogue.size(), schedulesCreated.size(), instrumentsCreated.size(), report.drift().size());
        return report;
    }

    private List<String> seedCatalogue() {
        List<String> created = new ArrayList<>();
        for (ChargeCatalogueEntity entry : readAll(resource(CATALOGUE), ChargeCatalogueEntity::new)) {
            // The gate every code enters through. A code is a Mongo field name downstream, so one
            // that cannot be used as a field name has to be refused here rather than at save time.
            ChargeCodes.validate(entry.getCode());
            if (chargeCatalogueRepository.existsByCode(entry.getCode())) {
                continue;
            }
            chargeCatalogueRepository.save(entry);
            created.add(entry.getCode());
            log.info("Seeded charge code {}", entry.getCode());
        }
        return created;
    }

    private void seedSchedules(List<String> created, List<String> skipped) {
        // Checked across the shipped set, not per file: an overlap is a relationship between two
        // cards, so no amount of validating one of them can see it.
        ChargeScheduleWindows.requireNoOverlap(scheduleFiles().stream()
                .map(file -> read(file, ChargeScheduleEntity::new))
                .toList());

        for (Resource file : scheduleFiles()) {
            ChargeScheduleEntity schedule = read(file, ChargeScheduleEntity::new);

            ChargeScheduleEntity onFile =
                    chargeScheduleRepository.findByScheduleCode(schedule.getScheduleCode()).orElse(null);
            if (onFile != null) {
                // Possibly edited since; the shipped version must not overwrite it. Said out loud,
                // because skipping quietly is how a database and the repository beside it come to
                // disagree about what every user is charged with nothing reporting the difference.
                List<String> differences = difference(onFile, schedule);
                if (!differences.isEmpty()) {
                    log.warn("Charge schedule {} on file differs from the shipped file {} in {}; "
                                    + "leaving the stored card alone. GET /charge-schedules/drift lists this.",
                            schedule.getScheduleCode(), file.getFilename(), differences);
                }
                skipped.add(schedule.getScheduleCode());
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
            created.add(schedule.getScheduleCode());
            log.info("Seeded charge schedule {} for {}", schedule.getScheduleCode(), schedule.getBrokerName());
        }
    }

    /**
     * Every shipped card that does not match the one on file, and every one not on file at all.
     *
     * <p>Read live rather than remembered from startup: a card published through the API after the
     * application came up is exactly the kind of divergence worth seeing, and a report that only
     * describes boot time would miss it.
     */
    public List<ChargeScheduleDrift> findDrift() {
        List<ChargeScheduleDrift> drift = new ArrayList<>();

        for (Resource file : scheduleFiles()) {
            ChargeScheduleEntity shipped = read(file, ChargeScheduleEntity::new);
            ChargeScheduleEntity onFile =
                    chargeScheduleRepository.findByScheduleCode(shipped.getScheduleCode()).orElse(null);

            if (onFile == null) {
                drift.add(ChargeScheduleDrift.absent(shipped.getScheduleCode(), file.getFilename()));
                continue;
            }
            List<String> differences = difference(onFile, shipped);
            if (!differences.isEmpty()) {
                drift.add(new ChargeScheduleDrift(shipped.getScheduleCode(), file.getFilename(), differences));
            }
        }
        return drift;
    }

    /**
     * The top-level fields on which a stored card and its shipped file disagree.
     *
     * <p>Compared as trees rather than field by field, so a rule added, removed or repriced inside
     * the embedded list is caught without this method having to know what a rule is. The database
     * owns {@code id} and {@code auditMetadata} and no file has them, so they are dropped first —
     * otherwise every card drifts and the report is noise nobody reads.
     */
    private List<String> difference(ChargeScheduleEntity onFile, ChargeScheduleEntity shipped) {
        JsonNode stored = comparable(onFile);
        JsonNode file = comparable(shipped);

        // One side's property names are enough: both are the same class serialised the same way, so
        // the sets are identical and scanning both was a loop that could not find anything the first
        // had missed. Mutation testing is what said so — deleting either scan changed no outcome.
        List<String> differing = new ArrayList<>();
        for (String field : sorted(file.propertyNames())) {
            if (!Objects.equals(stored.get(field), file.get(field))) {
                differing.add(field);
            }
        }
        return differing;
    }

    /** Ordered, so a report reads the same twice and a test can assert it without sorting first. */
    private static Set<String> sorted(Iterable<String> names) {
        Set<String> ordered = new TreeSet<>();
        names.forEach(ordered::add);
        return ordered;
    }

    private JsonNode comparable(ChargeScheduleEntity schedule) {
        ObjectNode node = (ObjectNode) objectMapper.valueToTree(schedule);
        DATABASE_OWNED.forEach(node::remove);
        return node;
    }

    /**
     * The scheme profiles the mutual fund cards depend on.
     *
     * <p>A profile is not a rate card and must not be read as one, which is why they live in their
     * own directory: the schedule pattern does not descend into it, so adding a profile cannot
     * accidentally be parsed as a schedule with every field null.
     */
    private void seedInstruments(List<String> created, List<String> skipped) {
        for (Resource file : seedFiles(INSTRUMENTS, "charge instrument profiles")) {
            ChargeInstrumentEntity instrument = read(file, ChargeInstrumentEntity::new);

            if (chargeInstrumentRepository
                    .findByStockCodeAndStartDate(instrument.getStockCode(), instrument.getStartDate())
                    .isPresent()) {
                // Possibly edited since; the shipped version must not overwrite it.
                skipped.add(instrument.getStockCode());
                continue;
            }

            try {
                chargeScheduleValidator.validate(instrument);
            } catch (BadRequestException e) {
                throw new IllegalStateException("Shipped charge instrument profile " + file.getFilename()
                        + " is invalid: " + e.getMessage(), e);
            }
            chargeInstrumentRepository.save(instrument);
            created.add(instrument.getStockCode());
            log.info("Seeded charge instrument profile for {}", instrument.getStockCode());
        }
    }

    /** Sorted, so seeding order is the same on every machine and a failure reproduces. */
    private List<Resource> scheduleFiles() {
        return seedFiles(SCHEDULES, "charge rate cards").stream()
                .filter(resource -> !CATALOGUE_FILE.equals(resource.getFilename()))
                .toList();
    }

    private List<Resource> seedFiles(String pattern, String what) {
        try {
            return Arrays.stream(resourceResolver.getResources(pattern))
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list the shipped " + what, e);
        }
    }


    private Resource resource(String location) {
        return resourceResolver.getResource(location);
    }

    /**
     * Reads a seed file into an instance this method constructs, rather than one Jackson does.
     *
     * <p>That is not a style preference. Lombok puts {@code @ConstructorProperties} on
     * {@code @AllArgsConstructor}, Jackson honours it as a creator, and a card built through it has
     * a <em>null</em> {@code auditMetadata} where a card built through the no-arg constructor has an
     * empty one. Spring Data's auditing fills an object; it does not create one. So a seeded card
     * used to be saved with no audit metadata at all, while every entity this application builds
     * normally — a transaction, say — records who wrote it and when.
     *
     * <p>Updating a pre-built instance keeps the initialiser and gives auditing something to write
     * into. Nothing here sets a single audit field: {@code SecurityAuditorAware} and
     * {@code @EnableMongoAuditing} do that, as they already did everywhere else.
     */
    <T> T read(Resource file, Supplier<T> factory) {
        try (InputStream stream = file.getInputStream()) {
            return objectMapper.readerForUpdating(factory.get()).readValue(stream);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not read the charge seed file " + file.getFilename() + ": " + e.getMessage(), e);
        }
    }

    /** The same, for a file holding an array of them. */
    private <T> List<T> readAll(Resource file, Supplier<T> factory) {
        List<T> values = new ArrayList<>();
        try (InputStream stream = file.getInputStream()) {
            for (JsonNode element : objectMapper.readTree(stream)) {
                values.add(objectMapper.readerForUpdating(factory.get()).readValue(element));
            }
            return values;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not read the charge seed file " + file.getFilename() + ": " + e.getMessage(), e);
        }
    }
}
