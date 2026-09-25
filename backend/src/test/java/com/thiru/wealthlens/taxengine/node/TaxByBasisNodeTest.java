package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tax at slab rates on the rounded total income, from the regime's own ladder in the shipped pack
 * ({@code DR 16} S2 step 5; {@code DR 23} A11). Every expected figure is worked by hand in
 * {@code plans/W-046.md}, not taken from a run.
 */
class TaxByBasisNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

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

    private static NodeResult run(RegimeCode regime, String roundedTotalIncome) {
        return NodeHarness.with(pack())
                .regime(regime)
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(roundedTotalIncome))
                .run(new TaxByBasisNode());
    }

    @Test
    void evaluate_whenNewRegimeIncomeIsTwelveLakh_isSixtyThousandAtSlabRates() {
        // Given — 4L–8L at 5% = 20,000; 8L–12L at 10% = 40,000 (DR 23 A11)
        // When
        NodeResult result = run(NEW_TAX_REGIME, "1200000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("60000")));
    }

    @Test
    void evaluate_whenNewRegimeIncomeIsAtTheTopOfTheNilBand_isZero() {
        // When
        NodeResult result = run(NEW_TAX_REGIME, "400000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.ZERO));
    }

    @Test
    void evaluate_whenNewRegimeIncomeIsOneRupeeIntoTheTopBand_chargesThirtyPaiseAndDoesNotRound() {
        // Given — 20,000 + 40,000 + 60,000 + 80,000 + 1,00,000 = 3,00,000; 30% of ₹1 = 0.30;
        // rounding is not this node's (RATE-ROUND-003)
        // When
        NodeResult result = run(NEW_TAX_REGIME, "2400001");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("300000.30")));
    }

    @Test
    void evaluate_whenFirstScheduleRatesIncomeIsTenLakh_usesThatRegimesLadder() {
        // Given — 2.5L–5L at 5% = 12,500; 5L–10L at 20% = 1,00,000; FA 2026 First Schedule
        // Part I-B Para A (I) states the same "₹ 112500" at ₹10,00,000
        // When
        NodeResult result = run(FIRST_SCHEDULE_RATES, "1000000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("112500")));
        assertThat(result.trace()).singleElement().extracting(TraceLine::rule)
                .isEqualTo(RuleIds.RATE_SLAB_FIRST_SCHEDULE);
    }

    @Test
    void evaluate_whenRun_explainsItselfInOneTaxLineNamingTheRegimesRuleAndItsProvenance() {
        // Given
        RulePack pack = pack();

        // When
        NodeResult result = run(NEW_TAX_REGIME, "1200000");

        // Then
        TraceLine line = result.trace().getFirst();
        assertThat(result.trace()).hasSize(1);
        assertThat(line.rule()).isEqualTo(RuleIds.RATE_SLAB_NEW_TAX_REGIME);
        assertThat(line.kind()).isEqualTo(StepKind.TAX);
        assertThat(line.node()).isEqualTo(NodeIds.TAX_BYBASIS);
        assertThat(line.provenance()).isEqualTo(pack.rule(RuleIds.RATE_SLAB_NEW_TAX_REGIME)
                .versionAt(NodeHarness.plainTaxpayer().temporal()).provenance());
    }

    @Test
    void run_whenRoundedTotalIncomeIsNotGiven_isRefusedBecauseTheNodeDeclaresIt() {
        // When / Then — the node reads only what it declares, and it declares the rounded income
        assertThatThrownBy(() -> NodeHarness.with(pack()).regime(NEW_TAX_REGIME).run(new TaxByBasisNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.ROUND_TOTALINCOME.value());
    }
}
