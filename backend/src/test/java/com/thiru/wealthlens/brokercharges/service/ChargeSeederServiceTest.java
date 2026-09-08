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
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

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

        seeder = seederWith(new PathMatchingResourcePatternResolver());
    }

    @Test
    void seed_parsesAndAcceptsEveryShippedCard() {
        // Given / When / Then — parsing and validation both happen inside seed()
        assertThatCode(() -> seeder.seed()).doesNotThrowAnyException();
        assertThat(seededSchedules()).isNotEmpty();
    }

    @Test
    void seed_shipsExactlyTheCardsThisApplicationClaimsTo() {
        // Given — every loop-based assertion below is vacuous on an empty list, so what actually
        // got seeded is pinned first
        seeder.seed();

        // When / Then
        assertThat(seededSchedules()).extracting(ChargeScheduleEntity::getScheduleCode)
                .containsExactlyInAnyOrder(
                        "ZERODHA_EQ_DELIVERY_2025_04",
                        "ZERODHA_EQ_INTRADAY_2025_04",
                        "ZERODHA_MF_2025_04",
                        "ZERODHA_MAINTENANCE_2025_04",
                        "UPSTOX_EQ_DELIVERY_2025_04",
                        "FYERS_EQ_DELIVERY_2025_04");
        assertThat(seededCatalogue()).hasSize(12);
    }

    @Test
    void seed_writesTheCatalogueBeforeTheCardsThatDependOnIt() {
        // Given — the validator rejects any rule code absent from the catalogue, so seeding a card
        // first would fail against an empty one
        seeder.seed();

        // Then
        assertThat(seededCatalogue()).isNotEmpty();
        assertThat(seededSchedules()).isNotEmpty();
    }

    @Test
    void everyRuleCodeIsInTheCatalogue() {
        // Given
        seeder.seed();
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
        seeder.seed();
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
        seeder.seed();

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
        seeder.seed();

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
        seeder.seed();

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

    // ---------------------------------------------------------- instrument profiles

    @Test
    void seed_shipsTheInstrumentProfilesTheMutualFundCardRequires() {
        // Given — ZERODHA_MF_2025_04 declares requiresInstrumentProfile, and until a profile is on
        // file every redemption it prices is recorded as NO_INSTRUMENT_PROFILE (AC-6)
        seeder.seed();

        // When / Then
        assertThat(seededProfiles()).extracting(ChargeInstrumentEntity::getStockCode)
                .containsExactlyInAnyOrder("PARAGPARIKHFLEXICAP", "HDFCLIQUID");
    }

    @Test
    void everyShippedProfileCarriesAnExitLoadThatIsPricedPerLot() {
        // Given — exit load is per FIFO lot, not per redemption. A rule that forgets perLot is
        // evaluated once over the whole disposal, which charges lots that no longer attract a load
        // and can be wrong by the entire charge rather than by a rounding error.
        seeder.seed();
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
        seeder.seed();
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
        seeder.seed();
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
        seeder.seed();
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
        seeder.seed();
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
    void everyCardSaysWhereItsRatesCameFromAndAdmitsTheyAreUnverified() {
        // Given — ADR-18. The rates shipped here are placeholders, and pretending otherwise would
        // make AC-2 look closed. sourceUrl is what makes verifying them possible; a null verifiedOn
        // is what puts the card in findUnverified() until someone has.
        seeder.seed();
        assertThat(seededSchedules()).isNotEmpty();

        // When / Then
        for (ChargeScheduleEntity schedule : seededSchedules()) {
            assertThat(schedule.getSourceUrl())
                    .as("sourceUrl of %s", schedule.getScheduleCode())
                    .isNotBlank().startsWith("https://");
            assertThat(schedule.getVerifiedOn())
                    .as("%s carries placeholder rates until a human checks them", schedule.getScheduleCode())
                    .isNull();
        }
    }

    @Test
    void everyCardDeclaresItsRulesActive() {
        // Given — active defaults to false on the entity, so a rule that forgets the field is
        // silently inert and its charge simply never appears
        seeder.seed();
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
        seeder.seed();

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
        assertThatThrownBy(() -> seeder.seed())
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
        assertThatThrownBy(() -> seederWith(validator).seed())
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
        assertThatThrownBy(() -> seederWith(validator).seed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("instrument profile")
                .hasMessageContaining(".json");
    }

    @Test
    void seed_evictsTheResolverCache() {
        // Given — the resolver may already have answered for a scope during startup
        seeder.seed();

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
        seeder.seed();

        // When / Then
        assertThat(seededCatalogue()).isNotEmpty().allSatisfy(entry ->
                assertThat(entry.getStatus()).as("status of %s", entry.getCode())
                        .isEqualTo(EntityStatus.ACTIVE));
    }

    @Test
    void seed_whenTheClasspathCannotBeListed_failsFast() {
        // Given — starting with no rate cards at all would price every trade at zero
        ResourcePatternResolver broken = mock(ResourcePatternResolver.class);
        when(broken.getResource(anyString()))
                .thenReturn(new PathMatchingResourcePatternResolver()
                        .getResource("classpath:data/charges/charge-catalogue.json"));
        assertThatThrownBy(() -> {
            when(broken.getResources(anyString())).thenThrow(new IOException("classpath unreadable"));
            seederWith(broken).seed();
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
        assertThatThrownBy(() -> seeder.read(malformed, ChargeScheduleEntity.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken-card.json");
    }

    private ChargeSeederService seederWith(ChargeScheduleValidator validator) {
        return new ChargeSeederService(chargeCatalogueRepository, chargeScheduleRepository,
                chargeInstrumentRepository, validator, chargeScheduleResolver, chargeInstrumentResolver,
                new PathMatchingResourcePatternResolver());
    }

    private ChargeSeederService seederWith(ResourcePatternResolver resolver) {
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
}
