package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BroughtForwardLoss;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Bucket;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Member;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Relationship;
import com.thiru.wealthlens.taxengine.input.FilingFacts.Payment;
import com.thiru.wealthlens.taxengine.rules.component.RateBasisHint;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionState;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The cache key. A component it cannot see is a wrong answer served from cache.
 */
class InputsFingerprintTest {

    private static final TaxYearId TY = TaxYearId.of(2026);
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 21);
    private static final Instant PF_AS_OF = Instant.parse("2026-09-01T00:00:00Z");

    /** The harness's plain taxpayer, with the one family member the family perturbations change. */
    private static EngineInputs baseline() {
        return copy(NodeHarness.plainTaxpayer(), r -> r.family = new FamilySnapshot(List.of(new Member(
                Relationship.CHILD,
                LocalDate.of(2010, 4, 4),
                true,
                Optional.empty(),
                Optional.empty(),
                false,
                false))));
    }

    private static LocalDate baselineDateOfBirth() {
        return ((Individual) NodeHarness.plainTaxpayer().assessee()).dateOfBirth();
    }

    private static String hashOf(EngineInputs inputs) {
        return InputsFingerprint.of(inputs).sha256();
    }

    @Nested
    @DisplayName("equal inputs give equal fingerprints")
    class Stability {

        @Test
        void twoIdenticallyBuiltInputSetsAgree() {
            assertThat(hashOf(baseline())).isEqualTo(hashOf(baseline()));
        }

        @Test
        void theFingerprintIsSixtyFourLowerCaseHexDigits() {
            assertThat(hashOf(baseline())).hasSize(64).matches("[0-9a-f]{64}");
        }

        @Test
        void aMalformedFingerprintIsRejected() {
            assertThatThrownBy(() -> new InputsFingerprint("NOTAHASH"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("64 lower-case hex digits");
        }

        @Test
        void fingerprintingNothingIsRejectedRatherThanHashingTheWordNull() {
            assertThatThrownBy(() -> InputsFingerprint.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * The property F6 got wrong. Every component of {@link EngineInputs} is perturbed in turn,
     * including nested ones, and each must move the hash.
     */
    @Nested
    @DisplayName("any field change gives a different fingerprint")
    class Sensitivity {

        static Stream<Arguments> perturbations() {
            return Stream.of(
                    Arguments.of("taxYear", mutate(in -> withTaxYear(in, TaxYearId.of(2025)))),
                    Arguments.of(
                            "entered.employments",
                            mutate(in -> copy(in, r -> r.entered = new EnteredFacts(List.of(new Employment(List.of(
                                    new SalaryComponent(new ComponentCode("BASIC"),
                                            com.thiru.wealthlens.taxengine.rules.money.Money.of("600000"))),
                                    false)), List.of(), List.of(), List.of(), Optional.empty())))),
                    Arguments.of(
                            "regime",
                            mutate(in -> copy(in, r -> r.regime = new RegimeCode("FIRST_SCHEDULE_RATES")))),
                    Arguments.of(
                            "temporal.asOfDate",
                            mutate(in -> copy(in, r -> r.temporal =
                                    TemporalContext.forTaxYear(TY, AS_OF.plusDays(1))))),
                    Arguments.of(
                            "assessee.dateOfBirth",
                            mutate(in -> copy(in, r -> r.assessee = new Individual(
                                    baselineDateOfBirth().minusYears(1),
                                    ResidentialStatus.RESIDENT,
                                    Optional.empty(),
                                    Optional.empty())))),
                    Arguments.of(
                            "assessee.residence",
                            mutate(in -> copy(in, r -> r.assessee = new Individual(
                                    baselineDateOfBirth(), ResidentialStatus.RNOR, Optional.empty(), Optional.empty())))),
                    Arguments.of(
                            "assessee.disability",
                            mutate(in -> copy(in, r -> r.assessee = new Individual(
                                    baselineDateOfBirth(),
                                    ResidentialStatus.RESIDENT,
                                    Optional.of(DisabilitySeverity.SEVERE_DISABILITY),
                                    Optional.empty())))),
                    Arguments.of(
                            "assessee.type — an HUF with the same residence",
                            mutate(in -> copy(in, r -> r.assessee = new Huf(ResidentialStatus.RESIDENT)))),
                    Arguments.of(
                            "family.members[0].dateOfBirth",
                            mutate(in -> copy(in, r -> r.family = new FamilySnapshot(List.of(
                                    new Member(
                                            Relationship.CHILD,
                                            LocalDate.of(2011, 4, 4),
                                            true,
                                            Optional.empty(),
                                            Optional.empty(),
                                            false,
                                            false)))))),
                    Arguments.of(
                            "ledgers.gratuityAlreadyAllowed",
                            mutate(in -> copy(in, r -> r.ledgers = new LedgerSnapshot(
                                    Map.of(),
                                    Optional.empty(),
                                    com.thiru.wealthlens.taxengine.rules.money.Money.of("100000"),
                                    com.thiru.wealthlens.taxengine.rules.money.Money.ZERO,
                                    0,
                                    Map.of(),
                                    Optional.empty())))),
                    Arguments.of(
                            "elections.stateByRegime",
                            mutate(in -> copy(in, r -> r.elections = new RegimeElectionSnapshot(
                                    Map.of(new RegimeCode("NEW_TAX_REGIME"), ElectionState.IN_EFFECT),
                                    Map.of())))),
                    Arguments.of(
                            "capitalGains.buckets",
                            mutate(in -> copy(in, r -> r.capitalGains = new CapitalGainsInput(
                                    List.of(new Bucket(
                                            com.thiru.wealthlens.taxengine.rules.money.Money.of("50000"),
                                            Term.LONG_TERM,
                                            RateBasisHint.SPECIAL_RATE_LISTED_EQUITY,
                                            Optional.empty())),
                                    List.of(),
                                    PF_AS_OF,
                                    "pf-run-1")))),
                    Arguments.of(
                            "capitalGains.asOf",
                            mutate(in -> copy(in, r -> r.capitalGains = CapitalGainsInput.none(
                                    PF_AS_OF.plusSeconds(1), "pf-run-1")))),
                    Arguments.of(
                            "capitalGains.broughtForward",
                            mutate(in -> copy(in, r -> r.capitalGains = new CapitalGainsInput(
                                    List.of(),
                                    List.of(new BroughtForwardLoss(
                                            TaxYearId.of(2024),
                                            Term.SHORT_TERM,
                                            com.thiru.wealthlens.taxengine.rules.money.Money.of("1000"))),
                                    PF_AS_OF,
                                    "pf-run-1")))),
                    Arguments.of(
                            "filing.advanceTaxPaid",
                            mutate(in -> copy(in, r -> r.filing = new FilingFacts(
                                    LocalDate.of(2026, 7, 31),
                                    Optional.empty(),
                                    List.of(new Payment(
                                            LocalDate.of(2026, 3, 15),
                                            com.thiru.wealthlens.taxengine.rules.money.Money.of(
                                                    "25000"))))))),
                    Arguments.of(
                            "filing.furnishedOn",
                            mutate(in -> copy(in, r -> r.filing = new FilingFacts(
                                    LocalDate.of(2026, 7, 31),
                                    Optional.of(LocalDate.of(2026, 8, 1)),
                                    List.of())))));
        }

        @ParameterizedTest(name = "{0} changes the fingerprint")
        @MethodSource("perturbations")
        void everyPerturbationMovesTheHash(String what, UnaryOperator<EngineInputs> perturb) {
            assertThat(hashOf(perturb.apply(baseline())))
                    .as("changing %s must change the cache key, or its answer is served stale", what)
                    .isNotEqualTo(hashOf(baseline()));
        }

        @Test
        void everyComponentOfEngineInputsIsCoveredByAPerturbation() {
            // Guards the guard: a component added to EngineInputs without a perturbation here
            // would leave this suite passing while the property went untested.
            List<String> perturbed = perturbations()
                    .map(a -> ((String) a.get()[0]).split("\\.")[0])
                    .distinct()
                    .toList();
            List<String> components = List.of(EngineInputs.class.getRecordComponents()).stream()
                    .map(RecordComponent::getName)
                    .toList();

            assertThat(perturbed).containsAll(components);
        }

        @Test
        void employment_whenOnlyTheTermsOnDearnessAllowanceDiffer_movesTheHash() {
            // Given — D-092: whether DA counts in the salary bases changes HRA and the pension limits
            EngineInputs silent = copy(baseline(), r -> r.entered = enteringBasic(false));
            EngineInputs providing = copy(baseline(), r -> r.entered = enteringBasic(true));

            // When / Then
            assertThat(hashOf(providing)).isNotEqualTo(hashOf(silent));
        }

        private static EnteredFacts enteringBasic(boolean termsIncludeDearnessAllowance) {
            return new EnteredFacts(List.of(new Employment(
                    List.of(new SalaryComponent(new ComponentCode("BASIC"),
                            com.thiru.wealthlens.taxengine.rules.money.Money.of("600000"))),
                    termsIncludeDearnessAllowance)), List.of(), List.of(), List.of(), Optional.empty());
        }

        @Test
        void enteredFacts_whenOnlyATenancyDiffers_movesTheHash() {
            // Given — D-099: the HRA exemption reads each tenancy, so it must be in the cache key
            EngineInputs withTenancy = copy(baseline(), r -> r.entered = new EnteredFacts(List.of(), List.of(
                    new Tenancy(com.thiru.wealthlens.taxengine.rules.temporal.DateRange.between(
                                    LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)), "BENGALURU", false,
                            com.thiru.wealthlens.taxengine.rules.money.Money.of("360000"))), List.of(), List.of(), Optional.empty()));

            // When / Then
            assertThat(hashOf(withTenancy)).isNotEqualTo(hashOf(baseline()));
        }

        @Test
        void enteredFacts_whenOnlyAJourneyDiffers_movesTheHash() {
            // Given — D-100: the LTA exemption reads each journey
            EngineInputs withJourney = copy(baseline(), r -> r.entered = new EnteredFacts(List.of(), List.of(),
                    List.of(new Journey(LocalDate.of(2026, 5, 10), JourneyMode.RAIL_CONNECTED,
                            com.thiru.wealthlens.taxengine.rules.money.Money.of("18000"),
                            Optional.of(com.thiru.wealthlens.taxengine.rules.money.Money.of("15000")),
                            Optional.empty())), List.of(), Optional.empty()));

            // When / Then
            assertThat(hashOf(withJourney)).isNotEqualTo(hashOf(baseline()));
        }

        @Test
        void ledgers_whenOnlyABlocksJourneysDiffer_movesTheHash() {
            // Given — D-100: journeys already claimed in block 2022-2025
            EngineInputs claimed = copy(baseline(), r -> r.ledgers = new LedgerSnapshot(
                    Map.of(2022, new LeaveTravelBlock(1, false)), Optional.empty(),
                    com.thiru.wealthlens.taxengine.rules.money.Money.ZERO,
                    com.thiru.wealthlens.taxengine.rules.money.Money.ZERO, 0, Map.of(), Optional.empty()));

            // When / Then
            assertThat(hashOf(claimed)).isNotEqualTo(hashOf(baseline()));
        }

        @Test
        void enteredFacts_whenOnlyAnAllowanceUseDiffers_movesTheHash() {
            // Given — D-101: the allowance exemptions read how each allowance was used
            EngineInputs withUse = copy(baseline(), r -> r.entered = new EnteredFacts(List.of(), List.of(), List.of(),
                    List.of(AllowanceUse.forChildren(new ComponentCode("CHILDREN_EDUCATION_ALLOWANCE"), 2, 12)), Optional.empty()));

            // When / Then
            assertThat(hashOf(withUse)).isNotEqualTo(hashOf(baseline()));
        }

        @Test
        void enteredFacts_whenOnlyTheDisabledTransportClaimDiffers_movesTheHash() {
            // Given — D-102: the transport exemption reads the condition, months, city and DA thereon
            EngineInputs withClaim = copy(baseline(), r -> r.entered = new EnteredFacts(List.of(), List.of(),
                    List.of(), List.of(), Optional.of(new DisabledTransportClaim(
                            com.thiru.wealthlens.taxengine.rules.definition.TransportDisability.BLIND, 12,
                            Optional.empty(), Optional.empty()))));

            // When / Then
            assertThat(hashOf(withClaim)).isNotEqualTo(hashOf(baseline()));
        }

        @Test
        void enteredFacts_whenEmpty_isStillInTheCanonicalForm() {
            // D-026: declared and thin rather than absent, so an empty entry is a value in the key too
            assertThat(InputsFingerprint.canonicalFormOf(baseline())).contains("entered=T:EnteredFacts{");
        }
    }

    @Nested
    @DisplayName("order that carries no meaning carries no hash")
    class Ordering {

        @Test
        void mapIterationOrderIsIrrelevant() {
            RegimeCode a = new RegimeCode("A_REGIME");
            RegimeCode b = new RegimeCode("B_REGIME");

            Map<RegimeCode, ElectionState> forward = new LinkedHashMap<>();
            forward.put(a, ElectionState.IN_EFFECT);
            forward.put(b, ElectionState.WITHDRAWN);
            Map<RegimeCode, ElectionState> reversed = new LinkedHashMap<>();
            reversed.put(b, ElectionState.WITHDRAWN);
            reversed.put(a, ElectionState.IN_EFFECT);

            assertThat(hashOf(copy(baseline(), r -> r.elections =
                            new RegimeElectionSnapshot(forward, Map.of()))))
                    .isEqualTo(hashOf(copy(baseline(), r -> r.elections =
                            new RegimeElectionSnapshot(reversed, Map.of()))));
        }

        @Test
        void recordComponentsAreHashedByNameNotDeclarationPosition() {
            // Sorted by name, so reordering a declaration is a refactor rather than a mass cache
            // invalidation. Visible in the canonical form: capitalGains precedes taxYear.
            String canonical = InputsFingerprint.canonicalFormOf(baseline());

            assertThat(canonical.indexOf("capitalGains=")).isLessThan(canonical.indexOf("taxYear="));
        }

        @Test
        void listOrderIsSignificantBecauseCollidingIsWorseThanMissing() {
            // FamilySnapshot says member order carries no meaning, but sorting could make two
            // genuinely different inputs collide -- a wrong cache hit. Preserving order can only
            // cause a needless miss.
            Member first = new Member(
                    Relationship.CHILD, LocalDate.of(2010, 4, 4), true,
                    Optional.empty(), Optional.empty(), false, false);
            Member second = new Member(
                    Relationship.PARENT, LocalDate.of(1955, 4, 4), true,
                    Optional.empty(), Optional.empty(), false, false);

            assertThat(hashOf(copy(baseline(), r -> r.family =
                            new FamilySnapshot(List.of(first, second)))))
                    .isNotEqualTo(hashOf(copy(baseline(), r -> r.family =
                            new FamilySnapshot(List.of(second, first)))));
        }
    }

    @Nested
    @DisplayName("the canonical form")
    class Canonical {

        @Test
        void distinguishesAStringFromANumber() {
            assertThat(InputsFingerprint.canonicalFormOf("5"))
                    .isNotEqualTo(InputsFingerprint.canonicalFormOf(5));
        }

        @Test
        void distinguishesAnEmptyOptionalFromAnAbsentValue() {
            assertThat(InputsFingerprint.canonicalFormOf(Optional.empty()))
                    .isNotEqualTo(InputsFingerprint.canonicalFormOf(null));
        }

        @Test
        void theRulePackIsNotInHere() {
            // RulePack.computationCacheKey() covers it, readably, and excludes editorial layers so
            // catalogue revisions invalidate nothing (G-11). Folding it in would undo that.
            assertThat(InputsFingerprint.canonicalFormOf(baseline()))
                    .doesNotContain("RulePackRef")
                    .doesNotContain("checksum");
        }
    }

    // -- a mutable builder, so each perturbation states only what it changes -------------------

    private static final class Draft {
        TaxYearId taxYear;
        RegimeCode regime;
        TemporalContext temporal;
        EnteredFacts entered;
        Assessee assessee;
        FamilySnapshot family;
        LedgerSnapshot ledgers;
        RegimeElectionSnapshot elections;
        CapitalGainsInput capitalGains;
        FilingFacts filing;
    }

    private static EngineInputs copy(EngineInputs in, java.util.function.Consumer<Draft> change) {
        Draft draft = new Draft();
        draft.taxYear = in.taxYear();
        draft.regime = in.regime();
        draft.temporal = in.temporal();
        draft.entered = in.entered();
        draft.assessee = in.assessee();
        draft.family = in.family();
        draft.ledgers = in.ledgers();
        draft.elections = in.elections();
        draft.capitalGains = in.capitalGains();
        draft.filing = in.filing();
        change.accept(draft);
        return new EngineInputs(
                draft.taxYear,
                draft.regime,
                draft.temporal,
                draft.entered,
                draft.assessee,
                draft.family,
                draft.ledgers,
                draft.elections,
                draft.capitalGains,
                draft.filing);
    }

    private static EngineInputs withTaxYear(EngineInputs in, TaxYearId year) {
        return copy(in, r -> {
            r.taxYear = year;
            r.temporal = TemporalContext.forTaxYear(year, AS_OF);
        });
    }

    private static UnaryOperator<EngineInputs> mutate(UnaryOperator<EngineInputs> op) {
        return op;
    }
}
