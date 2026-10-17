package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
        return new LedgerSnapshot(blocks, Optional.empty(), Money.ZERO, Money.ZERO, 0, Map.of(), Optional.empty());
    }

    private static Journey journey(String startedOn, JourneyMode mode, String incurred, String fare) {
        return new Journey(LocalDate.parse(startedOn), mode, Money.of(incurred), Optional.of(Money.of(fare)),
                Optional.empty());
    }

    private static NodeResult run(int year, RegimeCode regime, List<Journey> journeys, LedgerSnapshot ledgers,
            String ltaReceived) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())), List.of(), journeys, List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), plain.assessee(),
                plain.family(), ledgers, plain.elections(), plain.capitalGains(), plain.filing());
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
                Money.of("14000"), Optional.empty(), Optional.of(400));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, List.of(road), PREVIOUS_BLOCK_USED, "50000");

        // Then — r.278(1)(iii)(B): 30 × 400 = 12,000, less than the 14,000 spent
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("12000")));
    }

    @Test
    void evaluate_whenNoRailAndNoPublicTransportIn2025WithOnlyADistance_isRefused() {
        // Given — r.2B(1)(iii)(B) states a rail-fare equivalent, not a rate: the fare must be entered
        Journey road = new Journey(LocalDate.parse("2025-05-10"), JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                Money.of("14000"), Optional.empty(), Optional.of(400));

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
}
