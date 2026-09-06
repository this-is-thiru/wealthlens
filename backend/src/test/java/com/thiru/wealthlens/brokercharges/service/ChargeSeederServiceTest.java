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

import com.thiru.wealthlens.brokercharges.engine.ChargeFormulaEvaluator;
import com.thiru.wealthlens.brokercharges.engine.ChargeScheduleResolver;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
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
    private ChargeSeederService seeder;

    @BeforeEach
    void setUp() {
        chargeCatalogueRepository = mock(ChargeCatalogueRepository.class);
        chargeScheduleRepository = mock(ChargeScheduleRepository.class);
        chargeScheduleResolver = mock(ChargeScheduleResolver.class);

        // Nothing seeded yet, and the catalogue answers with whatever the seeder just wrote to it.
        when(chargeCatalogueRepository.existsByCode(anyString())).thenReturn(false);
        when(chargeScheduleRepository.findByScheduleCode(anyString())).thenReturn(Optional.empty());
        when(chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE))
                .thenAnswer(call -> seededCatalogue());

        seeder = new ChargeSeederService(chargeCatalogueRepository, chargeScheduleRepository,
                new ChargeScheduleValidator(chargeCatalogueRepository, new ChargeFormulaEvaluator()),
                chargeScheduleResolver, new PathMatchingResourcePatternResolver());
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

        // When
        seeder.seed();

        // Then
        verify(chargeScheduleRepository, never()).save(any());
        verify(chargeCatalogueRepository, never()).save(any());
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
    void seed_evictsTheResolverCache() {
        // Given — the resolver may already have answered for a scope during startup
        seeder.seed();

        // Then
        verify(chargeScheduleResolver).evictAll();
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

    private ChargeSeederService seederWith(ResourcePatternResolver resolver) {
        return new ChargeSeederService(chargeCatalogueRepository, chargeScheduleRepository,
                new ChargeScheduleValidator(chargeCatalogueRepository, new ChargeFormulaEvaluator()),
                chargeScheduleResolver, resolver);
    }

    // ---------------------------------------------------------------- helpers

    private List<ChargeCatalogueEntity> seededCatalogue() {
        ArgumentCaptor<ChargeCatalogueEntity> captor = ArgumentCaptor.forClass(ChargeCatalogueEntity.class);
        verify(chargeCatalogueRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
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
