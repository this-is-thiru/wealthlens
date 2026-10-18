package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Member;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Relationship;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
import com.thiru.wealthlens.taxengine.input.JourneyTraveller;
import com.thiru.wealthlens.taxengine.input.LeaveTravelBlock;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The leave travel concession in {@link ExemptionNode} (S4 r.278; Rules 1962 r.2B; D-100): per journey the lesser
 * of the amount incurred and the fare admitted for its mode; two journeys per four-calendar-year block, the
 * carried-over journey uncounted; never more than the assistance received. Each figure is worked by hand.
 */
class LeaveTravelExemptionTest {

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    /** Block 2022–2025 already used once, so nothing is carried into 2026–2029. */
    private static final LedgerSnapshot PREVIOUS_BLOCK_USED = ledger(Map.of(2022, new LeaveTravelBlock(1, false)));

    private static LedgerSnapshot ledger(Map<Integer, LeaveTravelBlock> blocks) {
        return new LedgerSnapshot(blocks, Optional.empty(), Money.ZERO, Money.ZERO, Map.of(), Optional.empty());
    }

    private static Journey journey(String startedOn, JourneyMode mode, String incurred, String fare) {
        return new Journey(LocalDate.parse(startedOn), mode, Money.of(incurred), Optional.of(Money.of(fare)),
                Optional.empty(), List.of());
    }

    private static NodeResult run(int year, RegimeCode regime, List<Journey> journeys, LedgerSnapshot ledgers,
            String ltaReceived) {
        return run(year, regime, journeys, ledgers, ltaReceived, NodeHarness.plainTaxpayer().family());
    }

    private static NodeResult run(int year, RegimeCode regime, List<Journey> journeys, LedgerSnapshot ledgers,
            String ltaReceived, FamilySnapshot family) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())), List.of(), journeys, List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()), plain.assessee(),
                family, ledgers, plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of(HeadCode.SALARIES,
                        List.of(new Line(0, ComponentCodes.LTA, Money.of(ltaReceived))))))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_HRA, List.of(Money.ZERO))))
                .run(new ExemptionNode());
    }

    @Test
    void evaluate_whenARailJourneyCostMoreThanTheFare_exemptsTheFare() {
        // When — 18,000 spent; the AC first-class rail fare is 15,000
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES,
                List.of(journey("2026-05-10", JourneyMode.RAIL_CONNECTED, "18000", "15000")), PREVIOUS_BLOCK_USED,
                "50000");

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_LEAVE_TRAVEL_CONCESSION);
            assertThat(line.kind()).isEqualTo(StepKind.EXEMPTION);
            assertThat(line.limitedBy()).contains("fare admissible");
        });
    }

    @Test
    void evaluate_whenAnAirJourneyCostLessThanTheFare_exemptsWhatWasSpent() {
        // When — 20,000 spent; the fare for the class the employee is entitled to is 25,000
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES,
                List.of(journey("2026-05-10", JourneyMode.AIR, "20000", "25000")), PREVIOUS_BLOCK_USED, "50000");

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("20000")));
        assertThat(result.trace().getFirst().limitedBy()).contains("amount actually incurred");
    }

    @Test
    void evaluate_whenNoRailAndNoPublicTransportIn2026_pricesThirtyRupeesAKilometre() {
        // Given — 400 km by the shortest route, 14,000 spent
        Journey road = new Journey(LocalDate.parse("2026-05-10"), JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                Money.of("14000"), Optional.empty(), Optional.of(400), List.of());

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, List.of(road), PREVIOUS_BLOCK_USED, "50000");

        // Then — r.278(1)(iii)(B): 30 × 400 = 12,000, less than the 14,000 spent
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("12000")));
    }

    @Test
    void evaluate_whenNoRailAndNoPublicTransportIn2025WithOnlyADistance_isRefused() {
        // Given — r.2B(1)(iii)(B) states a rail-fare equivalent, not a rate: the fare must be entered
        Journey road = new Journey(LocalDate.parse("2025-05-10"), JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                Money.of("14000"), Optional.empty(), Optional.of(400), List.of());

        // When / Then
        assertThatThrownBy(() -> run(2025, FIRST_SCHEDULE_RATES, List.of(road),
                        ledger(Map.of(2022, new LeaveTravelBlock(1, false))), "50000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fare");
    }

    @Test
    void evaluate_whenAThirdJourneyFallsInTheSameBlock_exemptsOnlyTwo() {
        // Given — three journeys in 2026, block 2026–2029, nothing carried in
        List<Journey> three = List.of(
                journey("2026-05-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"),
                journey("2026-07-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"),
                journey("2026-10-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, three, PREVIOUS_BLOCK_USED, "50000");

        // Then — 10,000 + 10,000 + nil
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("20000")));
        assertThat(result.trace()).extracting(line -> line.out().asMoney())
                .containsExactly(Money.of("10000"), Money.of("10000"), Money.ZERO);
        assertThat(result.trace().getLast().limitedBy()).hasValueSatisfying(why -> assertThat(why).contains("block"));
    }

    @Test
    void evaluate_whenThePreviousBlockWasUnused_carriesTheFirstJourneyUncounted() {
        // Given — block 2022–2025 unused; three journeys in 2026, the first calendar year of 2026–2029
        List<Journey> three = List.of(
                journey("2026-05-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"),
                journey("2026-07-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"),
                journey("2026-10-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, three, ledger(Map.of()), "50000");

        // Then — r.278(3)–(4): the first is the carried journey and not counted, so all three are exempt
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
    }

    @Test
    void evaluate_whenTheYearStraddlesTwoBlocks_countsEachAgainstItsOwnBlock() {
        // Given — TY 2025-26: May 2025 is in block 2022–2025 (already full); February 2026 is in 2026–2029
        List<Journey> two = List.of(
                journey("2025-05-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"),
                journey("2026-02-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"));

        // When
        NodeResult result = run(2025, FIRST_SCHEDULE_RATES, two,
                ledger(Map.of(2022, new LeaveTravelBlock(2, false))), "50000");

        // Then — R-12: nil for the first, 10,000 for the second
        assertThat(result.trace()).extracting(line -> line.out().asMoney())
                .containsExactly(Money.ZERO, Money.of("10000"));
    }

    @Test
    void evaluate_whenTheJourneysExceedTheAssistanceReceived_exemptsNoMoreThanWasReceived() {
        // Given — two journeys of 15,000 against 20,000 received
        List<Journey> two = List.of(
                journey("2026-05-10", JourneyMode.RAIL_CONNECTED, "15000", "15000"),
                journey("2026-07-10", JourneyMode.RAIL_CONNECTED, "15000", "15000"));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, two, PREVIOUS_BLOCK_USED, "20000");

        // Then — 15,000, then the 5,000 left
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("20000")));
        assertThat(result.trace()).extracting(line -> line.out().asMoney())
                .containsExactly(Money.of("15000"), Money.of("5000"));
        assertThat(result.trace().getLast().limitedBy()).contains("assistance received");
    }

    @Test
    void evaluate_whenInTheNewRegime_exemptsNothingAndSaysWhyForEachJourney() {
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME,
                List.of(journey("2026-05-10", JourneyMode.RAIL_CONNECTED, "10000", "10000"),
                        journey("2026-11-02", JourneyMode.AIR, "20000", "25000")), PREVIOUS_BLOCK_USED, "50000");

        // Then — s.202(2)(a)(i): nothing exempt, and a nil line per journey naming the regime (DR 08 T5-4; D-153)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).hasSize(2).allSatisfy(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_LEAVE_TRAVEL_CONCESSION);
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
            assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).startsWith("disallowed in the NEW_TAX_REGIME regime: ")
                        .endsWith("(" + RuleIds.REG_NEW_TAX_REGIME_NO_EXEMPTION + ")"));
        });
    }

    @Test
    void evaluate_whenAJourneyIsOutsideTheTaxYear_isRefused() {
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES,
                        List.of(journey("2026-02-10", JourneyMode.RAIL_CONNECTED, "10000", "10000")),
                        PREVIOUS_BLOCK_USED, "50000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-02-10");
    }

    /**
     * The two-children limit (S4 r.278(5)–(6); Rules 1962 r.2B(4); D-212). One rail journey: the employee 10,000 spent
     * against an 8,000 fare, and each child 6,000 against 5,000 — 28,000 against 23,000 for a family of five. By
     * hand: with every child covered, min(28,000, 23,000) = 23,000; with the youngest left out, min(22,000, 18,000) =
     * 18,000 and the youngest's 6,000 a nil line.
     */
    @Nested
    @DisplayName("the two-children limit")
    class TwoChildrenLimit {

        private static Member child(String bornOn, Optional<LocalDate> diedOn, boolean multipleBirthAfterFirstChild) {
            return new Member(Relationship.CHILD, LocalDate.parse(bornOn), true, Optional.empty(), diedOn,
                    multipleBirthAfterFirstChild, true);
        }

        private static Member child(String bornOn) {
            return child(bornOn, Optional.empty(), false);
        }

        private static JourneyTraveller traveller(Optional<Integer> member, String incurred, String fare) {
            return new JourneyTraveller(member, Money.of(incurred), Optional.of(Money.of(fare)));
        }

        /** The family of five's journey in TY {@code year}, split by traveller: family positions 0, 1 and 2. */
        private static Journey familyJourney(int year) {
            return new Journey(LocalDate.of(year, 5, 10), JourneyMode.RAIL_CONNECTED, Money.of("28000"),
                    Optional.of(Money.of("23000")), Optional.empty(), List.of(
                            traveller(Optional.empty(), "10000", "8000"),
                            traveller(Optional.of(0), "6000", "5000"),
                            traveller(Optional.of(1), "6000", "5000"),
                            traveller(Optional.of(2), "6000", "5000")));
        }

        private static NodeResult runFor(int year, Journey journey, Member... children) {
            return run(year, FIRST_SCHEDULE_RATES, List.of(journey), PREVIOUS_BLOCK_USED, "50000",
                    new FamilySnapshot(List.of(children)));
        }

        private static List<TraceLine> lines(NodeResult result, com.thiru.wealthlens.taxengine.rules.id.RuleId rule) {
            return result.trace().stream().filter(line -> line.rule().equals(rule)).toList();
        }

        @ParameterizedTest(name = "TY {0}")
        @ValueSource(ints = {2025, 2026})
        void evaluate_whenThreeChildrenBornAfterTheDateTravel_leavesTheYoungestOut(int year) {
            // When
            NodeResult result = runFor(year, familyJourney(year), child("2005-03-01"), child("2008-03-01"),
                    child("2012-03-01"));

            // Then — "shall not be available to more than two surviving children" (r.278(5); r.2B(4))
            assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("18000")));
            assertThat(lines(result, RuleIds.SAL_LEAVE_TRAVEL_CHILDREN)).singleElement().satisfies(line -> {
                assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("6000")));
                assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
                assertThat(line.limitedBy().orElseThrow()).contains("family position 2");
            });
            assertThat(lines(result, RuleIds.SAL_LEAVE_TRAVEL_CONCESSION)).singleElement()
                    .satisfies(line -> assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("22000"))));
        }

        @ParameterizedTest(name = "TY {0}")
        @ValueSource(ints = {2025, 2026})
        void evaluate_whenTheEldestWasBornBeforeOctober1998_coversAllThree(int year) {
            // When — "shall not apply in respect of children born before 1st October, 1998" (r.278(6); r.2B(4) proviso)
            NodeResult result = runFor(year, familyJourney(year), child("1997-06-01"), child("2005-03-01"),
                    child("2008-03-01"));

            // Then
            assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("23000")));
            assertThat(lines(result, RuleIds.SAL_LEAVE_TRAVEL_CHILDREN)).isEmpty();
        }

        @ParameterizedTest(name = "TY {0}")
        @ValueSource(ints = {2025, 2026})
        void evaluate_whenTheEldestWasBornOnFirstOctober1998_countsHer(int year) {
            // When — born on the day, not before it
            NodeResult result = runFor(year, familyJourney(year), child("1998-10-01"), child("2005-03-01"),
                    child("2008-03-01"));

            // Then
            assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("18000")));
        }

        @ParameterizedTest(name = "TY {0}")
        @ValueSource(ints = {2025, 2026})
        void evaluate_whenTwinsFollowOneChild_coversAllThree(int year) {
            // When — "and also in case of multiple births after one child"
            NodeResult result = runFor(year, familyJourney(year), child("2005-03-01"),
                    child("2008-03-01", Optional.empty(), true), child("2008-03-01", Optional.empty(), true));

            // Then
            assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("23000")));
        }

        @ParameterizedTest(name = "TY {0}")
        @ValueSource(ints = {2025, 2026})
        void evaluate_whenAChildHasDied_countsOnlyTheSurvivingChildren(int year) {
            // Given — "surviving children": position 1 died in 2020; the employee, positions 0 and 2 travel, 22,000
            // against 18,000
            Journey journey = new Journey(LocalDate.of(year, 5, 10), JourneyMode.RAIL_CONNECTED, Money.of("22000"),
                    Optional.of(Money.of("18000")), Optional.empty(), List.of(
                            traveller(Optional.empty(), "10000", "8000"), traveller(Optional.of(0), "6000", "5000"),
                            traveller(Optional.of(2), "6000", "5000")));

            // When
            NodeResult result = runFor(year, journey, child("2005-03-01"),
                    child("2008-03-01", Optional.of(LocalDate.of(2020, 1, 1)), false), child("2012-03-01"));

            // Then — two survive, so none is beyond: min(22,000, 18,000) = 18,000; counting the dead child would
            // leave position 2 out, 13,000
            assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("18000")));
            assertThat(lines(result, RuleIds.SAL_LEAVE_TRAVEL_CHILDREN)).isEmpty();
        }

        @Test
        void evaluate_whenAJourneyIsNotSplitAndAChildIsBeyondTheLimit_refuses() {
            // Given
            Journey whole = new Journey(LocalDate.of(2026, 5, 10), JourneyMode.RAIL_CONNECTED, Money.of("28000"),
                    Optional.of(Money.of("23000")), Optional.empty(), List.of());

            // When / Then
            assertThatThrownBy(() -> runFor(2026, whole, child("2005-03-01"), child("2008-03-01"),
                    child("2012-03-01")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("family positions [2]")
                    .hasMessageContaining("enter who travelled on the journey of 2026-05-10");
        }

        @Test
        void evaluate_whenAJourneyPricedPerKilometreCarriesAChildBeyondTheLimit_exemptsTheCoveredTravel() {
            // Given — no rail and no public transport, 400 km; the employee 8,000 and position 2 6,000
            Journey road = new Journey(LocalDate.of(2026, 5, 10), JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                    Money.of("14000"), Optional.empty(), Optional.of(400), List.of(
                            new JourneyTraveller(Optional.empty(), Money.of("8000"), Optional.empty()),
                            new JourneyTraveller(Optional.of(2), Money.of("6000"), Optional.empty())));

            // When
            NodeResult result = runFor(2026, road, child("2005-03-01"), child("2008-03-01"), child("2012-03-01"));

            // Then — min(8,000, 30 × 400 = 12,000) = 8,000; a headcount share of the rate would give 6,000
            assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("8000")));
            assertThat(lines(result, RuleIds.SAL_LEAVE_TRAVEL_CHILDREN)).singleElement().satisfies(line -> {
                assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("6000")));
                assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
                assertThat(line.limitedBy().orElseThrow()).contains("none of it is the child's");
            });
        }

        @Test
        void evaluate_whenThePerKilometreRateBindsWithAChildBeyondTheLimit_keepsTheWholeRate() {
            // Given — 300 km; the employee 6,000, position 0 5,000 and position 2 4,000
            Journey road = new Journey(LocalDate.of(2026, 5, 10), JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                    Money.of("15000"), Optional.empty(), Optional.of(300), List.of(
                            new JourneyTraveller(Optional.empty(), Money.of("6000"), Optional.empty()),
                            new JourneyTraveller(Optional.of(0), Money.of("5000"), Optional.empty()),
                            new JourneyTraveller(Optional.of(2), Money.of("4000"), Optional.empty())));

            // When
            NodeResult result = runFor(2026, road, child("2005-03-01"), child("2008-03-01"), child("2012-03-01"));

            // Then — min(11,000, 30 × 300 = 9,000) = 9,000; a headcount share (two of three) would give 6,000
            assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("9000")));
            assertThat(lines(result, RuleIds.SAL_LEAVE_TRAVEL_CHILDREN)).singleElement().satisfies(line -> {
                assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("4000")));
                assertThat(line.limitedBy().orElseThrow()).contains("none of it is the child's");
            });
        }

        @Test
        void evaluate_whenATravellerIsNotInTheFamily_refuses() {
            // Given — position 3 in a family of three
            Journey journey = new Journey(LocalDate.of(2026, 5, 10), JourneyMode.RAIL_CONNECTED, Money.of("16000"),
                    Optional.of(Money.of("13000")), Optional.empty(), List.of(
                            traveller(Optional.empty(), "10000", "8000"), traveller(Optional.of(3), "6000", "5000")));

            // When / Then
            assertThatThrownBy(() -> runFor(2026, journey, child("2005-03-01"), child("2008-03-01"),
                    child("2012-03-01")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("family position 3, but the family has 3 members");
        }
    }
}
