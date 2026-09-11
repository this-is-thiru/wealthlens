package com.thiru.wealthlens.brokercharges.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.SlabBandBasis;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeScheduleDrift;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeSeedReport;
import com.thiru.wealthlens.brokercharges.engine.ChargeFormulaEvaluator;
import com.thiru.wealthlens.brokercharges.engine.ChargeInstrumentResolver;
import com.thiru.wealthlens.brokercharges.engine.ChargeScheduleResolver;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeInstrumentRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.testsupport.LogCapture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The rate cards this application ships with.
 *
 * <p>The highest-value test in the plan, because a rate-card typo is the most likely future defect
 * and the least visible in review — a wrong digit in a JSON file looks exactly like a right one.
 * These run against the real files on the classpath, so a bad card fails the build rather than a
 * quarter of transactions.
 *
 * <p>Seeded rates are placeholders (ADR-18). {@code verifiedOn} is deliberately null on every card
 * until a human compares it against the broker's published page, and the test asserts that state
 * rather than pretending otherwise — see {@code everyCardSaysWhereItsRatesCameFrom}.
 */
class ChargeSeederServiceTest {

    private static final String AUDITOR = "ops@wealthlens.test";

    private ChargeCatalogueRepository chargeCatalogueRepository;
    private ChargeScheduleRepository chargeScheduleRepository;
    private ChargeScheduleResolver chargeScheduleResolver;
    private ChargeInstrumentRepository chargeInstrumentRepository;
    private ChargeInstrumentResolver chargeInstrumentResolver;
    private ChargeSeederService seeder;

    @BeforeEach
    void setUp() {
        chargeCatalogueRepository = mock(ChargeCatalogueRepository.class);
        chargeScheduleRepository = mock(ChargeScheduleRepository.class);
        chargeScheduleResolver = mock(ChargeScheduleResolver.class);
        chargeInstrumentRepository = mock(ChargeInstrumentRepository.class);
        chargeInstrumentResolver = mock(ChargeInstrumentResolver.class);

        // Nothing seeded yet, and the catalogue answers with whatever the seeder just wrote to it.
        when(chargeCatalogueRepository.existsByCode(anyString())).thenReturn(false);
        when(chargeScheduleRepository.findByScheduleCode(anyString())).thenReturn(Optional.empty());
        when(chargeInstrumentRepository.findByStockCodeAndStartDate(anyString(), any()))
                .thenReturn(Optional.empty());
        when(chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE))
                .thenAnswer(call -> seededCatalogue());

        seeder = seederWith(new GenericApplicationContext());
    }

    @Test
    void seed_parsesAndAcceptsEveryShippedCard() {
        // Given / When / Then — parsing and validation both happen inside seed()
        assertThatCode(() -> seeder.seed(AUDITOR)).doesNotThrowAnyException();
        assertThat(seededSchedules()).isNotEmpty();
    }

    @Test
    void seed_shipsExactlyTheCardsThisApplicationClaimsTo() {
        // Given — every loop-based assertion below is vacuous on an empty list, so what actually
        // got seeded is pinned first
        seeder.seed(AUDITOR);

        // When / Then
        assertThat(seededSchedules()).extracting(ChargeScheduleEntity::getScheduleCode)
                .containsExactlyInAnyOrder(
                        "ZERODHA_EQ_DELIVERY_2025_04",
                        "ZERODHA_EQ_DELIVERY_2026_03",
                        "ZERODHA_EQ_DELIVERY_2026_06",
                        "ZERODHA_EQ_INTRADAY_2025_04",
                        "ZERODHA_EQ_INTRADAY_2026_03",
                        "ZERODHA_MF_2025_04",
                        "ZERODHA_MAINTENANCE_2025_04",
                        "UPSTOX_EQ_DELIVERY_2025_04",
                        "UPSTOX_EQ_DELIVERY_2026_03",
                        "FYERS_EQ_DELIVERY_2025_04",
                        "FYERS_EQ_DELIVERY_2026_03");
        assertThat(seededCatalogue()).hasSize(12);
    }

    @Test
    void seed_writesTheCatalogueBeforeTheCardsThatDependOnIt() {
        // Given — the validator rejects any rule code absent from the catalogue, so seeding a card
        // first would fail against an empty one
        seeder.seed(AUDITOR);

        // Then
        assertThat(seededCatalogue()).isNotEmpty();
        assertThat(seededSchedules()).isNotEmpty();
    }

    @Test
    void everyRuleCodeIsInTheCatalogue() {
        // Given
        seeder.seed(AUDITOR);
        List<String> catalogue = seededCatalogue().stream().map(ChargeCatalogueEntity::getCode).toList();
        assertThat(seededSchedules()).isNotEmpty();

        // When / Then
        for (ChargeScheduleEntity schedule : seededSchedules()) {
            assertThat(schedule.getRules()).extracting(ChargeRule::getCode)
                    .as("codes in %s", schedule.getScheduleCode())
                    .isSubsetOf(catalogue);
        }
    }

    @Test
    void noTwoShippedCardsCoverTheSameScopeAtTheSameTime() {
        // Given — two cards a trade cannot choose between is a resolver error at trade time, and
        // this is where it should surface instead
        seeder.seed(AUDITOR);
        List<ChargeScheduleEntity> schedules = seededSchedules();
        assertThat(schedules).hasSizeGreaterThan(1);

        // When / Then
        for (int i = 0; i < schedules.size(); i++) {
            for (int j = i + 1; j < schedules.size(); j++) {
                ChargeScheduleEntity left = schedules.get(i);
                ChargeScheduleEntity right = schedules.get(j);
                assertThat(sameScope(left, right) && overlapInTime(left, right))
                        .as("%s and %s cover the same scope over the same dates",
                                left.getScheduleCode(), right.getScheduleCode())
                        .isFalse();
            }
        }
    }

    @Test
    void atMostOneShippedCardPerBrokerIsUnscoped() {
        // Given — an unscoped card matches every dimension of every trade, so it is the fallback
        // wherever no specific card exists. Two of them for one broker are indistinguishable.
        seeder.seed(AUDITOR);

        // When / Then
        assertThat(seededSchedules()).isNotEmpty();
        assertThat(seededSchedules().stream()
                .filter(schedule -> schedule.getAssetType() == null
                        && schedule.getSegment() == null
                        && schedule.getExchange() == null
                        && schedule.getPlanCode() == null)
                .map(ChargeScheduleEntity::getBrokerName)
                .toList())
                .doesNotHaveDuplicates();
    }

    @Test
    void theMaintenanceCardLeavesEveryTradeDimensionUnset() {
        // Given — an annual maintenance cycle names no scrip, no quantity and no asset type, so a
        // card declaring any of those dimensions is disqualified by the resolver and the cycle bills
        // nothing. The card has to be unscoped to be resolvable at all.
        seeder.seed(AUDITOR);

        // When
        ChargeScheduleEntity maintenance = seededSchedules().stream()
                .filter(schedule -> "ZERODHA_MAINTENANCE_2025_04".equals(schedule.getScheduleCode()))
                .findFirst()
                .orElseThrow();

        // Then
        assertThat(maintenance.getAssetType()).isNull();
        assertThat(maintenance.getSegment()).isNull();
        assertThat(maintenance.getExchange()).isNull();
        assertThat(maintenance.getPlanCode()).isNull();
    }

    @Test
    void theMaintenanceCardChargesOnlyOnAnAmcCycle() {
        // Given — it is the broker's unscoped card, so it is the fallback for every trade of that
        // broker in an asset type no specific card covers. A rule of its own reaching BUY or SELL
        // would price those trades as maintenance.
        seeder.seed(AUDITOR);

        // When
        ChargeScheduleEntity maintenance = seededSchedules().stream()
                .filter(schedule -> "ZERODHA_MAINTENANCE_2025_04".equals(schedule.getScheduleCode()))
                .findFirst()
                .orElseThrow();

        // Then
        assertThat(maintenance.getRules()).isNotEmpty().allSatisfy(rule ->
                assertThat(rule.getEvents()).as("events of %s", rule.getCode())
                        .containsExactly(ChargeEvent.AMC_CYCLE));
    }

    @Test
    void seed_handsEveryDocumentToMongoWithSomethingAuditingCanFill() {
        // Given — Spring Data's auditing populates an AuditMetadata; it does not create one. A card
        // Jackson built through @AllArgsConstructor carries null there, because Lombok stamps
        // @ConstructorProperties on it and Jackson honours that as a creator, so the field
        // initialiser never runs. Seeded cards were saved with no audit metadata at all while every
        // normally-built entity in this application records who wrote it.
        //
        // Who and when is Spring's job and is asserted against a real database in
        // ChargesIntegrationTest. What this pins is the precondition it needs.
        seeder.seed(AUDITOR);

        // When / Then
        assertThat(seededSchedules()).isNotEmpty().allSatisfy(schedule ->
                assertThat(schedule.getAuditMetadata())
                        .as("audit metadata of %s", schedule.getScheduleCode()).isNotNull());
        assertThat(seededProfiles()).isNotEmpty().allSatisfy(profile ->
                assertThat(profile.getAuditMetadata()).isNotNull());
        assertThat(seededCatalogue()).isNotEmpty().allSatisfy(entry ->
                assertThat(entry.getAuditMetadata()).isNotNull());
    }

    @Test
    void seed_reportsWhatItWrote() {
        // Given / When — "run it and see" is not an answer for something that writes the rate cards
        // every user is charged against
        ChargeSeedReport report = seeder.seed(AUDITOR);

        // Then
        assertThat(report.seededBy()).isEqualTo(AUDITOR);
        assertThat(report.catalogueCreated()).hasSize(12);
        assertThat(report.schedulesCreated()).hasSize(11);
        assertThat(report.instrumentsCreated()).containsExactlyInAnyOrder("PARAGPARIKHFLEXICAP", "HDFCLIQUID");
        assertThat(report.schedulesSkipped()).isEmpty();
        assertThat(report.wroteNothing()).isFalse();
    }

    @Test
    void seed_whenEverythingIsAlreadyOnFile_reportsThatItWroteNothing() {
        // Given — the second run of a deployment checklist, which must be boring
        when(chargeCatalogueRepository.existsByCode(anyString())).thenReturn(true);
        when(chargeScheduleRepository.findByScheduleCode(anyString()))
                .thenAnswer(call -> shipped(call.getArgument(0)));
        when(chargeInstrumentRepository.findByStockCodeAndStartDate(anyString(), any()))
                .thenReturn(Optional.of(new ChargeInstrumentEntity()));

        // When
        ChargeSeedReport report = seeder.seed(AUDITOR);

        // Then
        assertThat(report.wroteNothing()).isTrue();
        assertThat(report.schedulesSkipped()).hasSize(11);
        assertThat(report.drift()).isEmpty();
    }

    // ---------------------------------------------------------------- drift

    @Test
    void findDrift_whenTheDatabaseMatchesTheShippedFiles_reportsNothing() {
        // Given — the ordinary state. Every shipped card is on file exactly as shipped.
        when(chargeScheduleRepository.findByScheduleCode(anyString())).thenAnswer(call ->
                shipped(call.getArgument(0)));

        // When / Then
        assertThat(seeder.findDrift()).isEmpty();
    }

    @Test
    void findDrift_whenACardOnFileDiffersFromTheShippedFile_namesTheCardAndTheFields() {
        // Given — the failure this exists for. The seeder is idempotent by scheduleCode, so a card
        // already on file is skipped however far it has drifted from the file that ships beside it:
        // silently, for ever, with the repository and the database disagreeing about what every
        // user is charged. Skipping is right — an operator's correction must survive a restart —
        // but doing it without saying so is not.
        when(chargeScheduleRepository.findByScheduleCode(anyString())).thenAnswer(call -> {
            Optional<ChargeScheduleEntity> onFile = shipped(call.getArgument(0));
            onFile.ifPresent(card -> {
                if ("ZERODHA_EQ_DELIVERY_2025_04".equals(card.getScheduleCode())) {
                    card.getRules().stream()
                            .filter(rule -> "DP".equals(rule.getCode()))
                            .forEach(rule -> rule.setFlatAmount(99.0));
                    card.setVerifiedOn(null);
                }
            });
            return onFile;
        });

        // When
        List<ChargeScheduleDrift> drift = seeder.findDrift();

        // Then
        assertThat(drift).singleElement().satisfies(d -> {
            assertThat(d.scheduleCode()).isEqualTo("ZERODHA_EQ_DELIVERY_2025_04");
            assertThat(d.differingFields()).contains("rules", "verifiedOn");
        });
    }

    @Test
    void findDrift_whenAShippedCardIsNotOnFileAtAll_reportsItAsAbsent() {
        // Given — a card that a deployment has not applied yet, which is a different problem from a
        // card that was applied and then changed, and needs a different fix
        when(chargeScheduleRepository.findByScheduleCode(anyString())).thenReturn(Optional.empty());

        // When
        List<ChargeScheduleDrift> drift = seeder.findDrift();

        // Then
        assertThat(drift).isNotEmpty().allSatisfy(d ->
                assertThat(d.differingFields()).containsExactly("absent from the database"));
    }

    @Test
    void findDrift_ignoresTheFieldsTheDatabaseOwns() {
        // Given — a card read back from MongoDB carries audit metadata that no shipped file has:
        // who wrote it and when. Comparing those would report every single card as drifted, and a
        // report that is never empty is one nobody reads.
        //
        // The id is not enough to prove this. It is @JsonIgnore'd on the entity, so it never reaches
        // the comparison whatever this method does — which is exactly what mutation testing said
        // when deleting the exclusion left every test green.
        when(chargeScheduleRepository.findByScheduleCode(anyString())).thenAnswer(call -> {
            Optional<ChargeScheduleEntity> onFile = shipped(call.getArgument(0));
            onFile.ifPresent(card -> {
                card.setId("mongo-generated-id");
                stampAuditMetadata(card);
            });
            return onFile;
        });

        // When / Then
        assertThat(seeder.findDrift()).isEmpty();
    }

    @Test
    void seed_whenACardOnFileHasDrifted_warnsRatherThanOverwriting() {
        // Given — the operator's edit must survive, and must not survive quietly
        when(chargeScheduleRepository.findByScheduleCode(anyString())).thenAnswer(call -> {
            Optional<ChargeScheduleEntity> onFile = shipped(call.getArgument(0));
            onFile.ifPresent(card -> card.setSourceUrl("https://someone-edited-this.test"));
            return onFile;
        });

        try (LogCapture logs = LogCapture.on(ChargeSeederService.class)) {
            // When
            seeder.seed(AUDITOR);

            // Then
            verify(chargeScheduleRepository, never()).save(any());
            assertThat(logs.warnings()).anySatisfy(message ->
                    assertThat(message).contains("differs from the shipped file"));
        }
    }

    /**
     * What a stored card carries and no seed file does: who wrote it and when.
     *
     * <p>A card parsed from a file has none — the field is null on anything Jackson built — which is
     * precisely why comparing it would report every card in the database as drifted.
     */
    private static void stampAuditMetadata(ChargeScheduleEntity card) {
        card.getAuditMetadata().setCreatedBy("seeder");
        card.getAuditMetadata().setCreatedAt(LocalDateTime.of(2025, 4, 1, 9, 0));
        card.getAuditMetadata().setUpdatedAt(LocalDateTime.of(2026, 9, 8, 17, 30));
    }

    /** The shipped file, parsed fresh, standing in for a database that seeded it earlier. */
    private Optional<ChargeScheduleEntity> shipped(String scheduleCode) {
        try {
            return Arrays.stream(new PathMatchingResourcePatternResolver()
                            .getResources("classpath*:data/charges/*.json"))
                    .filter(resource -> !"charge-catalogue.json".equals(resource.getFilename()))
                    .map(resource -> seeder.read(resource, ChargeScheduleEntity::new))
                    .filter(card -> card.getScheduleCode().equals(scheduleCode))
                    .findFirst();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------- instrument profiles

    @Test
    void seed_shipsTheInstrumentProfilesTheMutualFundCardRequires() {
        // Given — ZERODHA_MF_2025_04 declares requiresInstrumentProfile, and until a profile is on
        // file every redemption it prices is recorded as NO_INSTRUMENT_PROFILE (AC-6)
        seeder.seed(AUDITOR);

        // When / Then
        assertThat(seededProfiles()).extracting(ChargeInstrumentEntity::getStockCode)
                .containsExactlyInAnyOrder("PARAGPARIKHFLEXICAP", "HDFCLIQUID");
    }

    @Test
    void everyShippedProfileCarriesAnExitLoadThatIsPricedPerLot() {
        // Given — exit load is per FIFO lot, not per redemption. A rule that forgets perLot is
        // evaluated once over the whole disposal, which charges lots that no longer attract a load
        // and can be wrong by the entire charge rather than by a rounding error.
        seeder.seed(AUDITOR);
        assertThat(seededProfiles()).isNotEmpty();

        // When / Then
        for (ChargeInstrumentEntity profile : seededProfiles()) {
            ChargeRule exitLoad = profile.getRules().stream()
                    .filter(rule -> "EXIT_LOAD".equals(rule.getCode()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(profile.getStockCode() + " carries no exit load"));

            assertThat(exitLoad.isPerLot()).as("%s prices its exit load per lot", profile.getStockCode())
                    .isTrue();
            assertThat(exitLoad.getEvents()).as("events of %s", profile.getStockCode())
                    .containsExactly(ChargeEvent.SELL);
            assertThat(exitLoad.isActive()).isTrue();
        }
    }

    @Test
    void everyShippedProfileDependsOnTheHoldingPeriod() {
        // Given — an exit load that reads no holding period is levied on every redemption forever,
        // which is the one thing this charge must not do (AC-6)
        seeder.seed(AUDITOR);
        assertThat(seededProfiles()).isNotEmpty();

        // When / Then — either a predicate over #holdingDays, or bands over it
        for (ChargeInstrumentEntity profile : seededProfiles()) {
            ChargeRule exitLoad = profile.getRules().stream()
                    .filter(rule -> "EXIT_LOAD".equals(rule.getCode()))
                    .findFirst().orElseThrow();

            boolean readsHoldingPeriod =
                    (exitLoad.getEligibility() != null && exitLoad.getEligibility().contains("holdingDays"))
                            || exitLoad.effectiveSlabBandBasis() == SlabBandBasis.HOLDING_DAYS;

            assertThat(readsHoldingPeriod)
                    .as("%s's exit load depends on how long the lot was held", profile.getStockCode())
                    .isTrue();
        }
    }

    @Test
    void everyProfileRuleCodeIsInTheCatalogue() {
        // Given — the same rejection the cards get. A profile is validated before it is persisted,
        // so an exit load misspelled in a seed file stops startup rather than pricing redemptions.
        seeder.seed(AUDITOR);
        List<String> catalogue = seededCatalogue().stream().map(ChargeCatalogueEntity::getCode).toList();
        assertThat(seededProfiles()).isNotEmpty();

        // When / Then
        for (ChargeInstrumentEntity profile : seededProfiles()) {
            assertThat(profile.getRules()).extracting(ChargeRule::getCode)
                    .as("codes in %s", profile.getStockCode())
                    .isSubsetOf(catalogue);
        }
    }

    @Test
    void everyProfileSaysWhereItsLoadCameFromAndAdmitsItIsUnverified() {
        // Given — ADR-18 applies to a scheme's own charges as much as to a broker's
        seeder.seed(AUDITOR);
        assertThat(seededProfiles()).isNotEmpty();

        // When / Then
        for (ChargeInstrumentEntity profile : seededProfiles()) {
            assertThat(profile.getSourceUrl()).as("sourceUrl of %s", profile.getStockCode())
                    .isNotBlank().startsWith("https://");
            assertThat(profile.getVerifiedOn()).as("%s carries a placeholder load", profile.getStockCode())
                    .isNull();
            assertThat(profile.getStatus()).isEqualTo(EntityStatus.ACTIVE);
        }
    }

    @Test
    void noTwoShippedProfilesCoverTheSameSchemeAtTheSameTime() {
        // Given — two profiles in force for one scheme are indistinguishable, and the instrument
        // resolver refuses both by name at redemption time. This is where that should surface.
        seeder.seed(AUDITOR);
        List<ChargeInstrumentEntity> profiles = seededProfiles();

        // When / Then
        for (int i = 0; i < profiles.size(); i++) {
            for (int j = i + 1; j < profiles.size(); j++) {
                assertThat(profiles.get(i).getStockCode())
                        .as("two profiles for one scheme")
                        .isNotEqualTo(profiles.get(j).getStockCode());
            }
        }
    }

    @Test
    void everyCardSaysWhereItsRatesCameFromAndWhenTheyWereChecked() {
        // Given — the inverse of what this asserted until AC-2 was closed on 2026-09-08. Every rate
        // was compared against the broker's published page that day, so sourceUrl says where to look
        // and verifiedOn says when someone last did. A card that loses either drops back into
        // findUnverified(), which is the worklist and must not fill up silently.
        seeder.seed(AUDITOR);
        assertThat(seededSchedules()).isNotEmpty();

        // When / Then
        for (ChargeScheduleEntity schedule : seededSchedules()) {
            assertThat(schedule.getSourceUrl())
                    .as("sourceUrl of %s", schedule.getScheduleCode())
                    .isNotBlank().startsWith("https://");
            assertThat(schedule.getVerifiedOn())
                    .as("%s must say when its rates were last checked", schedule.getScheduleCode())
                    .isNotNull();
        }
    }

    @Test
    void noShippedRuleStillCallsItselfAPlaceholder() {
        // Given — the notes were the other half of ADR-18's promise. A rule marked PLACEHOLDER on a
        // card carrying a verifiedOn date is one of the two lying, and the note is the thing a human
        // reads when deciding whether to trust a figure.
        seeder.seed(AUDITOR);

        // When / Then
        for (ChargeScheduleEntity schedule : seededSchedules()) {
            assertThat(schedule.getRules()).allSatisfy(rule ->
                    assertThat(rule.getNotes()).as("%s / %s", schedule.getScheduleCode(), rule.getCode())
                            .doesNotContain("PLACEHOLDER"));
        }
    }

    @Test
    void everyBrokersCardsFormOneUnbrokenTimeline() {
        // Given — three generations of the Zerodha delivery card now cover three windows. A gap
        // between them resolves to NO_SCHEDULE and prices trades in it at nothing, which is the
        // failure this arrangement exists to avoid; an overlap is refused by the resolver instead.
        seeder.seed(AUDITOR);

        Map<String, List<ChargeScheduleEntity>> byScope = seededSchedules().stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getBrokerName() + "/"
                        + schedule.getAssetType() + "/" + schedule.getSegment()));

        // When / Then
        for (List<ChargeScheduleEntity> generations : byScope.values()) {
            List<ChargeScheduleEntity> ordered = generations.stream()
                    .sorted(Comparator.comparing(ChargeScheduleEntity::getStartDate))
                    .toList();

            for (int i = 0; i < ordered.size() - 1; i++) {
                ChargeScheduleEntity earlier = ordered.get(i);
                ChargeScheduleEntity later = ordered.get(i + 1);

                assertThat(earlier.getEndDate())
                        .as("%s is superseded by %s and must close its window",
                                earlier.getScheduleCode(), later.getScheduleCode())
                        .isNotNull();
                assertThat(earlier.getEndDate().plusDays(1))
                        .as("%s ends the day before %s begins, with no day priced by neither",
                                earlier.getScheduleCode(), later.getScheduleCode())
                        .isEqualTo(later.getStartDate());
            }
            assertThat(ordered.getLast().getEndDate())
                    .as("the current card for %s must stay open-ended", ordered.getLast().getScheduleCode())
                    .isNull();
        }
    }

    @Test
    void everyCardDeclaresItsRulesActive() {
        // Given — active defaults to false on the entity, so a rule that forgets the field is
        // silently inert and its charge simply never appears
        seeder.seed(AUDITOR);
        assertThat(seededSchedules()).isNotEmpty();

        for (ChargeScheduleEntity schedule : seededSchedules()) {
            assertThat(schedule.getRules()).isNotEmpty();
            assertThat(schedule.getRules()).allSatisfy(rule ->
                    assertThat(rule.isActive()).as("%s in %s", rule.getCode(), schedule.getScheduleCode())
                            .isTrue());
        }
    }

    @Test
    void seed_whenACardIsAlreadyOnFile_doesNotWriteItAgain() {
        // Given — the seeder runs on every startup
        when(chargeCatalogueRepository.existsByCode(anyString())).thenReturn(true);
        when(chargeScheduleRepository.findByScheduleCode(anyString()))
                .thenReturn(Optional.of(new ChargeScheduleEntity()));
        when(chargeInstrumentRepository.findByStockCodeAndStartDate(anyString(), any()))
                .thenReturn(Optional.of(new ChargeInstrumentEntity()));

        // When
        seeder.seed(AUDITOR);

        // Then
        verify(chargeScheduleRepository, never()).save(any());
        verify(chargeCatalogueRepository, never()).save(any());
        verify(chargeInstrumentRepository, never()).save(any());
    }

    @Test
    void seed_whenACardIsInvalid_failsFastRatherThanStartingWithIt() {
        // Given — an empty catalogue makes every rule code unknown, which is what a genuinely
        // malformed card looks like to the validator (AC-9)
        when(chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE)).thenReturn(List.of());

        // When / Then — the application must not come up quietly pricing trades from a bad card
        assertThatThrownBy(() -> seeder.seed(AUDITOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("charge");
    }

    @Test
    void seed_whenOnlyTheRateCardIsInvalid_stillFailsAndNamesTheCard() {
        // Given — the test above cannot tell which of the two validations fired: an empty catalogue
        // invalidates the cards and the profiles at once, so either check alone satisfies it.
        // Mutation testing proved that by deleting each call in turn with every test still green.
        // Here only the card is rejected, so nothing but the card's own validation can save it.
        ChargeScheduleValidator validator = mock(ChargeScheduleValidator.class);
        org.mockito.Mockito.doThrow(new BadRequestException("bad card"))
                .when(validator).validate(any(ChargeScheduleEntity.class));

        // When / Then — and it names the file rather than the rule, because that is what gets opened
        assertThatThrownBy(() -> seederWith(validator).seed(AUDITOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate card")
                .hasMessageContaining(".json");
    }

    @Test
    void seed_whenOnlyAnInstrumentProfileIsInvalid_stillFailsAndNamesTheProfile() {
        // Given — the other half. A scheme profile carries exit load, which no rate card can
        // express, so a profile accepted unchecked is a charge nothing else in the application
        // would have caught.
        ChargeScheduleValidator validator = mock(ChargeScheduleValidator.class);
        org.mockito.Mockito.doThrow(new BadRequestException("bad profile"))
                .when(validator).validate(any(ChargeInstrumentEntity.class));

        // When / Then
        assertThatThrownBy(() -> seederWith(validator).seed(AUDITOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("instrument profile")
                .hasMessageContaining(".json");
    }

    @Test
    void seed_evictsTheResolverCache() {
        // Given — the resolver may already have answered for a scope during startup
        seeder.seed(AUDITOR);

        // Then — both resolvers cache misses as well as hits, so a profile seeded after one has
        // answered is invisible until eviction
        verify(chargeScheduleResolver).evictAll();
        verify(chargeInstrumentResolver).evictAll();
    }

    @Test
    void everyShippedCatalogueEntryIsActive() {
        // Given — the validator loads the catalogue with findByStatus(ACTIVE). An entry that omits
        // its status is invisible to that query, so every card naming its code is rejected — one
        // missing field in one file breaking every rate card in the application.
        seeder.seed(AUDITOR);

        // When / Then
        assertThat(seededCatalogue()).isNotEmpty().allSatisfy(entry ->
                assertThat(entry.getStatus()).as("status of %s", entry.getCode())
                        .isEqualTo(EntityStatus.ACTIVE));
    }

    @Test
    void seed_whenTheClasspathCannotBeListed_failsFast() {
        // Given — starting with no rate cards at all would price every trade at zero
        ApplicationContext broken = mock(ApplicationContext.class);
        when(broken.getResource(anyString()))
                .thenReturn(new PathMatchingResourcePatternResolver()
                        .getResource("classpath:data/charges/charge-catalogue.json"));
        assertThatThrownBy(() -> {
            when(broken.getResources(anyString())).thenThrow(new IOException("classpath unreadable"));
            seederWith(broken).seed(AUDITOR);
        }).isInstanceOf(IllegalStateException.class).hasMessageContaining("rate cards");
    }

    @Test
    void read_whenAFileIsNotTheShapeItShouldBe_failsNamingTheFile() {
        // Given — a hand-edited seed file with a stray comma or a wrong type
        Resource malformed = new ByteArrayResource("{ \"scheduleCode\": ".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "broken-card.json";
            }
        };

        // When / Then
        assertThatThrownBy(() -> seeder.read(malformed, ChargeScheduleEntity::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken-card.json");
    }

    private ChargeSeederService seederWith(ChargeScheduleValidator validator) {
        return new ChargeSeederService(chargeCatalogueRepository, chargeScheduleRepository,
                chargeInstrumentRepository, validator, chargeScheduleResolver, chargeInstrumentResolver,
                new GenericApplicationContext());
    }

    private ChargeSeederService seederWith(ApplicationContext resolver) {
        return new ChargeSeederService(chargeCatalogueRepository, chargeScheduleRepository,
                chargeInstrumentRepository,
                new ChargeScheduleValidator(chargeCatalogueRepository, new ChargeFormulaEvaluator()),
                chargeScheduleResolver, chargeInstrumentResolver, resolver);
    }

    // ---------------------------------------------------------------- helpers

    private List<ChargeCatalogueEntity> seededCatalogue() {
        ArgumentCaptor<ChargeCatalogueEntity> captor = ArgumentCaptor.forClass(ChargeCatalogueEntity.class);
        verify(chargeCatalogueRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private List<ChargeInstrumentEntity> seededProfiles() {
        ArgumentCaptor<ChargeInstrumentEntity> captor = ArgumentCaptor.forClass(ChargeInstrumentEntity.class);
        verify(chargeInstrumentRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private List<ChargeScheduleEntity> seededSchedules() {
        ArgumentCaptor<ChargeScheduleEntity> captor = ArgumentCaptor.forClass(ChargeScheduleEntity.class);
        verify(chargeScheduleRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private static boolean sameScope(ChargeScheduleEntity left, ChargeScheduleEntity right) {
        return left.getBrokerName() == right.getBrokerName()
                && Objects.equals(left.getAssetType(), right.getAssetType())
                && Objects.equals(left.getSegment(), right.getSegment())
                && Objects.equals(left.getExchange(), right.getExchange())
                && Objects.equals(left.getPlanCode(), right.getPlanCode());
    }

    private static boolean overlapInTime(ChargeScheduleEntity left, ChargeScheduleEntity right) {
        boolean leftEndsBefore = left.getEndDate() != null && left.getEndDate().isBefore(right.getStartDate());
        boolean rightEndsBefore = right.getEndDate() != null && right.getEndDate().isBefore(left.getStartDate());
        return !leftEndsBefore && !rightEndsBefore;
    }

    // ========================================
    // A charge code becomes a Mongo field name
    // ========================================

    /**
     * {@code amount_by_code} is keyed by charge code, so a code is a <em>field name</em> in MongoDB —
     * and Mongo rejects field names containing a dot or starting with {@code $}. Such a code passes
     * the catalogue check, prices correctly, and then fails when the row is saved: the charge is
     * computed and lost. Rejected at the gate every code enters through instead.
     */
    @Test
    void seed_whenACatalogueCodeContainsADot_refusesIt() {
        // Given / When / Then
        assertThatThrownBy(() -> ChargeCodes.validate("MTF.INTEREST"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("MTF.INTEREST");
    }

    @Test
    void seed_whenACatalogueCodeStartsWithADollar_refusesIt() {
        assertThatThrownBy(() -> ChargeCodes.validate("$STT"))
                .isInstanceOf(BadRequestException.class);
    }

    /**
     * Mongo's unique index is case-sensitive, so {@code BROKERAGE} and {@code brokerage} would be two
     * catalogue rows for one charge. Rules name codes exactly, so the second is unreachable — and a
     * charge nobody can name is worse than one that was refused.
     */
    @Test
    void seed_whenACatalogueCodeIsNotUppercase_refusesIt() {
        assertThatThrownBy(() -> ChargeCodes.validate("brokerage"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("upper case");
    }

    @Test
    void seed_acceptsTheShapeEveryShippedCodeUses() {
        // The twelve shipped codes, and the shape a new one must follow.
        for (String code : List.of("BROKERAGE", "STT", "EXCHANGE_TXN", "SEBI_FEE", "IPFT",
                "STAMP_DUTY", "DP", "GST", "AMC", "ACCOUNT_OPENING", "EXIT_LOAD", "MF_TXN_FEE")) {
            ChargeCodes.validate(code);
        }
    }

    @Test
    void seed_whenACatalogueCodeIsBlank_refusesIt() {
        assertThatThrownBy(() -> ChargeCodes.validate("  "))
                .isInstanceOf(BadRequestException.class);
    }

    // ========================================
    // Overlapping validity windows
    // ========================================

    /**
     * {@code findCandidates} matches {@code start_date <= d <= end_date}, inclusive at both ends, so
     * a card ending on the day its successor starts makes both candidates for that day. The
     * validator checks one card in isolation and cannot see it; publishing supersedes and cannot
     * produce it. Seeding several files can, and the result is a day priced by whichever card the
     * resolver's tie-break happens to prefer.
     */
    @Test
    void seed_whenTwoShippedCardsShareAScopeAndOverlap_refusesThem() {
        // Given — same scope, and the first ends on the day the second begins
        ChargeScheduleEntity first = card("A", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 1));
        ChargeScheduleEntity second = card("B", LocalDate.of(2026, 3, 1), null);

        // When / Then
        assertThatThrownBy(() -> ChargeScheduleWindows.requireNoOverlap(List.of(first, second)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("A")
                .hasMessageContaining("B");
    }

    /** The shape the shipped generations actually use: one ends the day before the next begins. */
    @Test
    void seed_whenGenerationsAbutWithoutOverlapping_acceptsThem() {
        ChargeScheduleWindows.requireNoOverlap(List.of(
                card("A", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 2, 28)),
                card("B", LocalDate.of(2026, 3, 1), null)));
    }

    /** Two open-ended cards for one scope is the same fault, reached a different way. */
    @Test
    void seed_whenTwoCardsForOneScopeAreBothOpenEnded_refusesThem() {
        assertThatThrownBy(() -> ChargeScheduleWindows.requireNoOverlap(List.of(
                card("A", LocalDate.of(2025, 4, 1), null),
                card("B", LocalDate.of(2026, 3, 1), null))))
                .isInstanceOf(BadRequestException.class);
    }

    /** Different scopes may overlap freely — that is how delivery and intraday coexist. */
    @Test
    void seed_whenOverlappingCardsHaveDifferentScopes_acceptsThem() {
        ChargeScheduleEntity delivery = card("A", LocalDate.of(2025, 4, 1), null);
        ChargeScheduleEntity intraday = card("B", LocalDate.of(2025, 4, 1), null);
        intraday.setSegment(TradeSegment.INTRADAY);

        ChargeScheduleWindows.requireNoOverlap(List.of(delivery, intraday));
    }

    private static ChargeScheduleEntity card(String code, LocalDate from, LocalDate to) {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode(code);
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setAssetType(AssetType.EQUITY);
        schedule.setSegment(TradeSegment.DELIVERY);
        schedule.setStartDate(from);
        schedule.setEndDate(to);
        return schedule;
    }
}
