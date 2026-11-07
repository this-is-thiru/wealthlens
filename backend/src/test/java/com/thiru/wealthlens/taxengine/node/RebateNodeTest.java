package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tax after the s.156 rebates, each from its own pack row and only in its own regime (D-074). Every
 * expected figure is copied from {@code DR 08} T1 or {@code DR 13} T13-1, never computed.
 */
class RebateNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final SpecialRateId LTCG = new SpecialRateId("LTCG");

    private static RulePack pack() {
        return new ClasspathRulePackProvider(
                        "rulepacks", Set.of(TaxYearId.of(2026)), new JsonPackAssembler(instruments()))
                .load(TaxYearId.of(2026));
    }

    private static GoverningInstruments instruments() {
        Instrument act = Instrument.assented(
                "Income-tax Act, 2025",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-22082025-265620",
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                VerificationLevel.SOURCE_CHECKED);
        Instrument rules = Instrument.notified(
                "Income-tax Rules, 2026",
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-20032026-271092",
                URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                VerificationLevel.SOURCE_CHECKED);
        return new GoverningInstruments(act, act, rules);
    }

    private static NodeResult run(RegimeCode regime, String totalIncome, TaxByBasis tax) {
        return runFor(regime, totalIncome, tax, NodeHarness.plainTaxpayer());
    }

    private static NodeResult runFor(RegimeCode regime, String totalIncome, TaxByBasis tax, EngineInputs inputs) {
        return NodeHarness.with(pack())
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(totalIncome))
                .given(NodeIds.TAX_BYBASIS, tax)
                .run(new RebateNode());
    }

    private static TaxByBasis slab(String tax) {
        return TaxByBasis.atSlabRatesOnly(Money.of(tax));
    }

    private static TraceLine lineFor(NodeResult result, RuleId rule) {
        return result.trace().stream().filter(line -> line.rule().equals(rule)).findFirst().orElseThrow();
    }

    // -- the default regime, s.156(2) --------------------------------------------------------------

    @Test
    void evaluate_whenT1N1AtTheRebateCeiling_leavesNoTax() {
        // Given — DR 08 T1-N1: "REBATE-NEW-001: TI ≤ 12,00,000 → min(60,000 , 60,000) = 60,000;
        // tax after rebate = 0"
        // When
        NodeResult result = run(NEW_TAX_REGIME, "1200000", slab("60000"));

        // Then
        assertThat(result.value()).isEqualTo(slab("0"));
        assertThat(lineFor(result, RuleIds.REBATE_NEW_TAX_REGIME).out())
                .isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @Test
    void evaluate_whenT1N2OneRoundingStepAbove_leavesTheTenRupeesOfExcess() {
        // Given — DR 08 T1-N2: "rebate = 60,001.50 − 10 = 59,991.50; tax after rebate = 10.00"
        // When
        NodeResult result = run(NEW_TAX_REGIME, "1200010", slab("60001.50"));

        // Then
        assertThat(result.value()).isEqualTo(slab("10.00"));
        assertThat(lineFor(result, RuleIds.REBATE_NEW_TAX_REGIME_EXCESS).out())
                .isEqualTo(MoneyValue.of(Money.of("59991.50")));
    }

    @Test
    void evaluate_whenT1N3InsideTheReliefBand_leavesFiftyThousand() {
        // Given — DR 08 T1-N3: "rebate = 67,500 − 50,000 = 17,500; tax after rebate = 50,000"
        // When
        NodeResult result = run(NEW_TAX_REGIME, "1250000", slab("67500"));

        // Then
        assertThat(result.value()).isEqualTo(slab("50000"));
    }

    @Test
    void evaluate_whenT1N4JustBelowTheCrossover_stillRelievesSevenRupees() {
        // Given — DR 08 T1-N4: "70,587.00 > 70,580 → rebate = 7.00; tax after rebate = 70,580.00"
        // When
        NodeResult result = run(NEW_TAX_REGIME, "1270580", slab("70587.00"));

        // Then
        assertThat(result.value()).isEqualTo(slab("70580.00"));
    }

    @Test
    void evaluate_whenT1N4JustAboveTheCrossover_relievesNothing() {
        // Given — DR 08 T1-N4: "70,588.50 > 70,590 ? NO → rebate = 0; tax after rebate = 70,588.50"
        // When
        NodeResult result = run(NEW_TAX_REGIME, "1270590", slab("70588.50"));

        // Then
        assertThat(result.value()).isEqualTo(slab("70588.50"));
    }

    @Test
    void evaluate_whenT13_1TheSlabTaxIsNil_givesNoRebateAgainstSpecialRateTax() {
        // Given — DR 13 T13-1: "Rebate is NIL (REBATE-NEW-MR-002, s.156(3)); the special-rate tax
        // stands". DR 13 leaves the special tax as ⟨special⟩, so this figure is arbitrary
        TaxByBasis tax = new TaxByBasis(Money.ZERO, Map.of(LTCG, Money.of("123456")));

        // When
        NodeResult result = run(NEW_TAX_REGIME, "1200000", tax);

        // Then
        assertThat(result.value()).isEqualTo(tax);
        TraceLine rebate = lineFor(result, RuleIds.REBATE_NEW_TAX_REGIME);
        assertThat(rebate.out()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(rebate.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).contains("tax at slab rates"));
    }

    @Test
    void evaluate_whenANonResidentIsAtTheRebateCeiling_explainsTheNilRebateByResidence() {
        // Given — s.156(2): "a resident individual assessee"
        EngineInputs nonResident = withResidence(ResidentialStatus.NON_RESIDENT);

        // When
        NodeResult result = runFor(NEW_TAX_REGIME, "1200000", slab("60000"), nonResident);

        // Then
        assertThat(result.value()).isEqualTo(slab("60000"));
        TraceLine rebate = lineFor(result, RuleIds.REBATE_NEW_TAX_REGIME);
        assertThat(rebate.out()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(rebate.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).contains("resident"));
    }

    @Test
    void evaluate_whenInTheDefaultRegime_emitsOneReliefLinePerRebateZerosIncluded() {
        // When
        NodeResult result = run(NEW_TAX_REGIME, "1200000", slab("60000"));

        // Then
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(
                RuleIds.REBATE_NEW_TAX_REGIME, RuleIds.REBATE_NEW_TAX_REGIME_EXCESS);
        assertThat(result.trace()).allSatisfy(line -> {
            assertThat(line.kind()).isEqualTo(StepKind.RELIEF);
            assertThat(line.node()).isEqualTo(NodeIds.TAX_AFTERREBATE);
            assertThat(line.limitedBy()).isPresent();
        });
        assertThat(lineFor(result, RuleIds.REBATE_NEW_TAX_REGIME_EXCESS).out())
                .isEqualTo(MoneyValue.of(Money.ZERO));
    }

    // -- the First Schedule rates, s.156(1) --------------------------------------------------------

    @Test
    void evaluate_whenT1O1AtTheOldRebateCeiling_leavesNoTax() {
        // Given — DR 08 T1-O1: "REBATE-OLD-001: TI ≤ 5,00,000 → min(12,500 , 12,500) = 12,500;
        // tax after rebate = 0"
        // When
        NodeResult result = run(FIRST_SCHEDULE_RATES, "500000", slab("12500"));

        // Then
        assertThat(result.value()).isEqualTo(slab("0"));
    }

    @Test
    void evaluate_whenT1O2OneRoundingStepAbove_hasNoMarginalReliefAndExplainsTheNil() {
        // Given — DR 08 T1-O2 (the H-3 test): "REBATE-OLD-001: TI > 5,00,000 → unavailable = 0;
        // REBATE-NEW-MR-001: DOES NOT APPLY … tax after rebate = 12,502"
        // When
        NodeResult result = run(FIRST_SCHEDULE_RATES, "500010", slab("12502"));

        // Then
        assertThat(result.value()).isEqualTo(slab("12502"));
        TraceLine only = result.trace().getFirst();
        assertThat(result.trace()).hasSize(1);
        assertThat(only.rule()).isEqualTo(RuleIds.REBATE_RESIDENT_INDIVIDUAL);
        assertThat(only.out()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(only.limitedBy()).isPresent();
    }

    @Test
    void evaluate_whenTheUnlimitedRebateMeetsSpecialRateTax_isRefusedAsUnsourced() {
        // Given — s.156(1) has no s.156(3)-style limit; how it meets special-rate tax is W-089's
        TaxByBasis tax = new TaxByBasis(Money.of("5000"), Map.of(LTCG, Money.of("1000")));

        // When / Then
        assertThatThrownBy(() -> run(FIRST_SCHEDULE_RATES, "400000", tax))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RuleIds.REBATE_RESIDENT_INDIVIDUAL.value())
                .hasMessageContaining("W-089");
    }

    // -- what the node reads ---------------------------------------------------------------------

    @Test
    void run_whenTaxByBasisIsNotGiven_isRefusedBecauseTheNodeDeclaresIt() {
        // When / Then
        assertThatThrownBy(() -> NodeHarness.with(pack())
                        .regime(NEW_TAX_REGIME)
                        .given(NodeIds.ROUND_TOTALINCOME, Money.of("1200000"))
                        .run(new RebateNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.TAX_BYBASIS.value());
    }

    private static EngineInputs withResidence(ResidentialStatus residence) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(), plain.entered(),
                new Individual(LocalDate.of(1985, 6, 15), residence, Optional.empty(), Optional.empty()),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }
}
