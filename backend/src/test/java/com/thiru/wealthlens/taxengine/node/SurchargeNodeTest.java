package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.Recomputation;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The surcharge on the tax after rebate, at the greater of the two ladders' rates, less the W = U + V
 * relief (D-084, D-085, D-086). Every expected figure is copied from {@code DR 08} T2 (VERIFIED, both years);
 * none is computed here.
 */
class SurchargeNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    /**
     * Tax after rebate at each threshold a case prices U at — what re-entry would return. From {@code DR 08}:
     * T2-1 (new, 50L: 10,80,000), T2-4 (first schedule, 2Cr: "[1,12,500 + 1,90,00,000 × 0.30] = 58,12,500";
     * 5Cr: 1,48,12,500); new regime at 2Cr is 3,00,000 + 0.30 × 1,76,00,000 = 55,80,000 by the same table.
     */
    private static final Map<RegimeCode, Map<Money, Money>> TAX_AT_THRESHOLD = Map.of(
            NEW_TAX_REGIME, Map.of(Money.of("5000000"), Money.of("1080000"), Money.of("20000000"), Money.of("5580000")),
            FIRST_SCHEDULE_RATES,
            Map.of(Money.of("20000000"), Money.of("5812500"), Money.of("50000000"), Money.of("14812500")));

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
    }

    /** Answers only for tax after rebate at a threshold the table knows; anything else is a test defect. */
    private static Recomputation atThresholds(RegimeCode regime) {
        return (target, pinned, value) -> {
            assertThat(target).isEqualTo(NodeIds.TAX_AFTERREBATE);
            assertThat(pinned).isEqualTo(NodeIds.ROUND_TOTALINCOME);
            Money tax = TAX_AT_THRESHOLD.get(regime).get(value.asMoney());
            assertThat(tax).as("tax after rebate at " + value.asMoney() + " under " + regime).isNotNull();
            return TaxByBasis.atSlabRatesOnly(tax);
        };
    }

    private static NodeResult run(int year, RegimeCode regime, String totalIncome, TaxByBasis afterRebate) {
        return NodeHarness.with(pack(year))
                .regime(regime)
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(totalIncome))
                .given(NodeIds.TAX_AFTERREBATE, afterRebate)
                .recomputation(atThresholds(regime))
                .run(new SurchargeNode());
    }

    private static NodeResult run(int year, RegimeCode regime, String totalIncome, String afterRebate) {
        return run(year, regime, totalIncome, TaxByBasis.atSlabRatesOnly(Money.of(afterRebate)));
    }

    static Stream<Arguments> t2Cases() {
        return Stream.of(2025, 2026).flatMap(year -> Stream.of(
                // T2-1: "TI does not EXCEED 50,00,000 → no surcharge"
                Arguments.of(year, "T2-1", NEW_TAX_REGIME, "5000000", "1080000", "0"),
                // T2-2: surcharge 1,08,000.30, relief 1,07,993.30
                Arguments.of(year, "T2-2", NEW_TAX_REGIME, "5000010", "1080003", "7.00"),
                // T2-3: surcharge 1,12,835.70, "relief = 2.70 → binds"
                Arguments.of(year, "T2-3a", NEW_TAX_REGIME, "5161190", "1128357", "112833.00"),
                // T2-3: surcharge 1,12,836, "NO → no relief"
                Arguments.of(year, "T2-3b", NEW_TAX_REGIME, "5161200", "1128360", "112836"),
                // T2-4 old: "0.25 × 1,48,12,500 = 37,03,125"; ceiling far above
                Arguments.of(year, "T2-4", FIRST_SCHEDULE_RATES, "50000000", "14812500", "3703125"),
                // T2-4 new: "exceeds 2,00,00,000 → 25%. THERE IS NO 37% BAND." 0.25 × 1,45,80,000
                Arguments.of(year, "T2-4", NEW_TAX_REGIME, "50000000", "14580000", "3645000"),
                // T2-5: surcharge 54,80,626.11, relief 17,77,494.11
                Arguments.of(year, "T2-5", FIRST_SCHEDULE_RATES, "50000010", "14812503", "3703132.00")));
    }

    @ParameterizedTest(name = "TY {0} {1} {2} at {3}")
    @MethodSource("t2Cases")
    void evaluate_whenTheT2CaseIsRun_chargesTheRelievedSurcharge(
            int year, String golden, RegimeCode regime, String totalIncome, String afterRebate, String surcharge) {
        // When
        NodeResult result = run(year, regime, totalIncome, afterRebate);

        // Then
        assertThat(result.value()).as(golden).isEqualTo(MoneyValue.of(Money.of(surcharge)));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenReliefBinds_tracesTheUnrelievedSurchargeThenTheRelief(int year) {
        // When — T2-2
        NodeResult result = run(year, NEW_TAX_REGIME, "5000010", "1080003");

        // Then
        assertThat(result.trace()).extracting(TraceLine::rule, TraceLine::kind, TraceLine::out)
                .containsExactly(
                        tuple(RuleIds.RATE_SUR_NEW_TAX_REGIME, StepKind.TAX,
                                MoneyValue.of(Money.of("108000.30"))),
                        tuple(RuleIds.RATE_SUR_NEW_TAX_REGIME_CEILING,
                                StepKind.RELIEF, MoneyValue.of(Money.of("107993.30"))));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheExcludingLadderWins_citesItAndNamesTheLadderItBeat(int year) {
        // When — T2-4 new: 25% on the excluding income beats 15% on the total income
        NodeResult result = run(year, NEW_TAX_REGIME, "50000000", "14580000");

        // Then
        TraceLine charge = result.trace().getFirst();
        assertThat(charge.rule()).isEqualTo(RuleIds.RATE_SUR_NEW_TAX_REGIME_EXCLUDING);
        assertThat(charge.limitedBy()).hasValueSatisfying(
                reason -> assertThat(reason).contains(RuleIds.RATE_SUR_NEW_TAX_REGIME.value()));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenNoSurchargeIsDue_stillExplainsTheNilRelief(int year) {
        // When — T2-1
        NodeResult result = run(year, NEW_TAX_REGIME, "5000000", "1080000");

        // Then — P5: an unavailable relief is explained, never absent
        assertThat(result.trace()).extracting(TraceLine::kind).containsExactly(StepKind.TAX, StepKind.RELIEF);
        assertThat(result.trace().getLast().out()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace().getLast().limitedBy()).isPresent();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenRunUnderOneRegime_leavesNoLineForTheOthersRows(int year) {
        // When
        NodeResult result = run(year, NEW_TAX_REGIME, "50000000", "14580000");

        // Then
        assertThat(result.trace()).extracting(line -> line.rule().value())
                .noneMatch(rule -> rule.startsWith("RATE-SUR-FIRST-SCHEDULE"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheTaxCarriesSpecialRateTax_isRefusedUntilTheSplitExists(int year) {
        // Given — RATE-SUR-SPLIT-001 and the excluding income's special gains are W-088's (D-086)
        TaxByBasis withGains = TaxByBasis.atSlabRatesOnly(Money.of("14580000"))
                .plusSpecialRate(new SpecialRateId("LTCG"), Money.of("100000"));

        // When / Then
        assertThatThrownBy(() -> run(year, NEW_TAX_REGIME, "50000000", withGains))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("W-088");
    }

    /** A taxpayer whose only entry is a dividend: what the excluding-income test needs to see (D-194 C4a). */
    private static EngineInputs withDividend(int year, RegimeCode regime) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        OtherSourceItem dividend = new OtherSourceItem(OtherSourceKind.DIVIDEND, Money.of("1000000"), Money.ZERO,
                Money.ZERO, Money.ZERO, false);
        return new EngineInputs(taxYear, regime, TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                        List.of(), List.of(), List.of(dividend)),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenDividendsMeetTheExcludingIncomeBand_refusesUntilTheSplitIsBuilt(int year) {
        // Given — T2-4's 5,00,00,000 under the first schedule, part of it a dividend: the excluding ladder's 25% bites
        NodeHarness harness = NodeHarness.with(pack(year))
                .regime(FIRST_SCHEDULE_RATES)
                .inputs(withDividend(year, FIRST_SCHEDULE_RATES))
                .given(NodeIds.ROUND_TOTALINCOME, Money.of("50000000"))
                .given(NodeIds.TAX_AFTERREBATE, TaxByBasis.atSlabRatesOnly(Money.of("14812500")))
                .recomputation(atThresholds(FIRST_SCHEDULE_RATES));

        // When / Then — taking the dividend out of the test could lower the rate: refused, naming W-079e
        assertThatThrownBy(() -> harness.run(new SurchargeNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W-079e");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenDividendsAreBelowTheExcludingIncomeBand_chargesAsBefore(int year) {
        // When — T2-1's 50,00,000 in the new regime, part of it a dividend: the excluding ladder is nil there
        NodeResult result = NodeHarness.with(pack(year))
                .regime(NEW_TAX_REGIME)
                .inputs(withDividend(year, NEW_TAX_REGIME))
                .given(NodeIds.ROUND_TOTALINCOME, Money.of("5000000"))
                .given(NodeIds.TAX_AFTERREBATE, TaxByBasis.atSlabRatesOnly(Money.of("1080000")))
                .recomputation(atThresholds(NEW_TAX_REGIME))
                .run(new SurchargeNode());

        // Then — "TI does not EXCEED 50,00,000 → no surcharge"
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }
}
