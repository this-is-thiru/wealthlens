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
import org.junit.jupiter.api.Test;
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
        return withItems(year, regime, item(OtherSourceKind.DIVIDEND, "1000000", "0"));
    }

    private static OtherSourceItem item(OtherSourceKind kind, String received, String interestExpense) {
        return new OtherSourceItem(kind, Money.of(received), Money.ZERO, Money.of(interestExpense), Money.ZERO, false);
    }

    /** A taxpayer whose only entries are these items of income from other sources. */
    private static EngineInputs withItems(int year, RegimeCode regime, OtherSourceItem... items) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(taxYear, regime, TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                        List.of(), List.of(), List.of(items), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheIncomeLessDividendsStillMeetsTheExcludingBand_isRefusedUntilTheLimitIsMeasured(int year) {
        // Given — T2-4's 5,00,00,000 under the first schedule, 10,00,000 of it a dividend: 4,90,00,000 still bites
        NodeHarness harness = NodeHarness.with(pack(year))
                .regime(FIRST_SCHEDULE_RATES)
                .inputs(withDividend(year, FIRST_SCHEDULE_RATES))
                .given(NodeIds.ROUND_TOTALINCOME, Money.of("50000000"))
                .given(NodeIds.TAX_AFTERREBATE, TaxByBasis.atSlabRatesOnly(Money.of("14812500")))
                .recomputation(atThresholds(FIRST_SCHEDULE_RATES));

        // When / Then — the dividends' own tax would bear at most 15%, and measuring it is OQ-26: refused
        assertThatThrownBy(() -> harness.run(new SurchargeNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-26")
                .hasMessageContaining("W-088");
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

    private static NodeResult runWith(int year, RegimeCode regime, String totalIncome, String afterRebate,
            OtherSourceItem... items) {
        return harnessWith(year, regime, totalIncome, afterRebate, items).run(new SurchargeNode());
    }

    private static NodeHarness harnessWith(int year, RegimeCode regime, String totalIncome, String afterRebate,
            OtherSourceItem... items) {
        return NodeHarness.with(pack(year))
                .regime(regime)
                .inputs(withItems(year, regime, items))
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(totalIncome))
                .given(NodeIds.TAX_AFTERREBATE, TaxByBasis.atSlabRatesOnly(Money.of(afterRebate)))
                .recomputation(atThresholds(regime));
    }

    /*
     * Tax after rebate at 2,50,00,000 by the same slab tables as TAX_AT_THRESHOLD: first schedule
     * 1,12,500 + 0.30 × 2,40,00,000 = 73,12,500; new regime 3,00,000 + 0.30 × 2,26,00,000 = 70,80,000. At 5,00,00,000
     * (first schedule) DR 08 T2-4's 1,48,12,500. Relief: U at 2,00,00,000 is that table's tax × 1.15, and with V the
     * income above it every W below exceeds the unrelieved tax and surcharge, so each relief is nil.
     */

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenDividendsTakeTheIncomeToTheExcludingThresholdExactly_chargesFifteenPerCent(int year) {
        // When — DR 08 T2-6's shape with a dividend: 2,50,00,000 of which 50,00,000 is dividend
        NodeResult result = runWith(year, FIRST_SCHEDULE_RATES, "25000000", "7312500",
                item(OtherSourceKind.DIVIDEND, "5000000", "0"));

        // Then — excluding 2,00,00,000 does not exceed 2,00,00,000; clause (v): 0.15 × 73,12,500
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1096875")));
        assertThat(result.trace().get(0).rule()).isEqualTo(RuleIds.RATE_SUR_FIRST_SCHEDULE);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenDividendsKeepTheNewRegimesExcludingIncomeAtTheThreshold_chargesFifteenPerCent(int year) {
        // When — new regime, 2,50,00,000 of which 50,00,000 is dividend
        NodeResult result = runWith(year, NEW_TAX_REGIME, "25000000", "7080000",
                item(OtherSourceKind.DIVIDEND, "5000000", "0"));

        // Then — clause (iv): 0.15 × 70,80,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1062000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheIncomeLessDividendsIsJustOverTheThreshold_isRefused(int year) {
        // Given — 2,50,00,000 of which 49,99,990 is dividend: 2,00,00,010 exceeds 2,00,00,000
        NodeHarness harness = harnessWith(year, FIRST_SCHEDULE_RATES, "25000000", "7312500",
                item(OtherSourceKind.DIVIDEND, "4999990", "0"));

        // When / Then
        assertThatThrownBy(() -> harness.run(new SurchargeNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-26");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenLargeDividendsTakeTheExcludingIncomeOutOfTheTopBands_chargesFifteenPerCent(int year) {
        // When — 5,00,00,000 of which 3,00,00,000 is dividend: excluding 2,00,00,000
        NodeResult result = runWith(year, FIRST_SCHEDULE_RATES, "50000000", "14812500",
                item(OtherSourceKind.DIVIDEND, "30000000", "0"));

        // Then — 0.15 × 1,48,12,500, not 25% of it
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("2221875")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheIncomeIsUnitIncome_keepsItInTheExcludingIncome(int year) {
        // When — 5,00,00,000 of which 3,00,00,000 is Mutual Fund unit income: not dividend (S2 s.2(40); D-200 C2a)
        NodeResult result = runWith(year, FIRST_SCHEDULE_RATES, "50000000", "14812500",
                item(OtherSourceKind.UNIT_INCOME, "30000000", "0"));

        // Then — DR 08 T2-4: 0.25 × 1,48,12,500
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("3703125")));
        assertThat(result.trace().get(0).rule()).isEqualTo(RuleIds.RATE_SUR_FIRST_SCHEDULE_EXCLUDING);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheDividendIsDeemed_takesItOutToo(int year) {
        // When — as the T2-6 shape, the 50,00,000 a deemed dividend (S2 s.2(40)(e), (f))
        NodeResult result = runWith(year, FIRST_SCHEDULE_RATES, "25000000", "7312500",
                item(OtherSourceKind.DEEMED_DIVIDEND, "5000000", "0"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1096875")));
    }

    @Test
    void evaluate_whenTaxYear2025AllowsInterestAgainstTheDividend_takesOutTheDividendLessIt() {
        // Given — 50,00,000 of dividend bearing 1,000 of interest; 20% × 50,00,000 = 10,00,000, so all 1,000 allowed
        NodeHarness harness = harnessWith(2025, FIRST_SCHEDULE_RATES, "25000000", "7312500",
                item(OtherSourceKind.DIVIDEND, "5000000", "1000"));

        // When / Then — dividend income 49,99,000: excluding 2,00,01,000 exceeds the threshold
        assertThatThrownBy(() -> harness.run(new SurchargeNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-26");
    }

    @Test
    void evaluate_whenTaxYear2026AllowsNoInterestAgainstTheDividend_takesOutTheWholeDividend() {
        // When — the same facts in TY 2026-27, where the deduction is nil (FA 2026 s.44(b))
        NodeResult result = runWith(2026, FIRST_SCHEDULE_RATES, "25000000", "7312500",
                item(OtherSourceKind.DIVIDEND, "5000000", "1000"));

        // Then — dividend income 50,00,000: excluding 2,00,00,000; 0.15 × 73,12,500
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1096875")));
    }

    /*
     * TY 2025-26, where the 1961 s.57 proviso's 20% limit binds over interest claimed against dividends and units both:
     * the allowed interest is the dividends' in proportion to their income (D-229), bounded by each side's claim
     * (D-234). Each case sits at the excluding threshold, so the dividends' share decides the ladder. Relief is nil:
     * at 1,00,00,000 the tax 28,12,500 × 1.10 plus the 1,07,00,000 or more above it exceeds every unrelieved figure.
     */

    @Test
    void evaluate_whenTheYearsLimitBindsOverDividendsAndUnitsBoth_attributesTheAllowanceByIncome() {
        // Given — 10,00,000 dividend bearing 3,00,000 interest, 5,00,000 units bearing 1,00,000; allowed
        // min(4,00,000, 20% × 15,00,000) = 3,00,000; the dividends' 3,00,000 × 10/15 = 2,00,000
        NodeResult result = runWith(2025, FIRST_SCHEDULE_RATES, "20800000", "6052500",
                item(OtherSourceKind.DIVIDEND, "1000000", "300000"), item(OtherSourceKind.UNIT_INCOME, "500000",
                        "100000"));

        // Then — dividend income 8,00,000: excluding 2,00,00,000 does not exceed the threshold; 0.15 × 60,52,500
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("907875")));
    }

    @Test
    void evaluate_whenTheDividendsIncomeShareExceedsTheirClaim_attributesOnlyTheirClaim() {
        // Given — 10,00,000 dividend bearing 1,00,000, 10,00,000 units bearing 5,00,000; allowed
        // min(6,00,000, 4,00,000) = 4,00,000; the income share 2,00,000 is more than the 1,00,000 claimed
        NodeResult result = runWith(2025, FIRST_SCHEDULE_RATES, "20900000", "6082500",
                item(OtherSourceKind.DIVIDEND, "1000000", "100000"), item(OtherSourceKind.UNIT_INCOME, "1000000",
                        "500000"));

        // Then — dividend income 9,00,000: excluding 2,00,00,000; 0.15 × 60,82,500. The bare share would leave
        // 2,01,00,000, over the threshold
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("912375")));
    }

    @Test
    void evaluate_whenTheUnitsIncomeShareExceedsTheirClaim_attributesTheRestToTheDividends() {
        // Given — 10,00,000 dividend bearing 5,00,000, 10,00,000 units bearing 1,00,000; allowed 4,00,000; the
        // units' share 2,00,000 is more than their 1,00,000, so the dividends' is 4,00,000 − 1,00,000 = 3,00,000
        NodeHarness harness = harnessWith(2025, FIRST_SCHEDULE_RATES, "20800000", "6052500",
                item(OtherSourceKind.DIVIDEND, "1000000", "500000"), item(OtherSourceKind.UNIT_INCOME, "1000000",
                        "100000"));

        // When / Then — dividend income 7,00,000: excluding 2,01,00,000 still reaches the 25% ladder with dividends
        // present, which is W-088's; the bare share would leave 2,00,00,000 and charge 15%
        assertThatThrownBy(() -> harness.run(new SurchargeNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W-088");
    }
}
