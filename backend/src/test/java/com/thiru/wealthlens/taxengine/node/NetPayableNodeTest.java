package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
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
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The amount payable before filing effects: the tax after rebate plus the cess (D-080). Every figure is
 * copied from {@code DR 08}'s "total" lines, except the special-rate case, worked by hand in
 * {@code plans/W-052.md}.
 */
class NetPayableNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

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

    private static NodeResult run(TaxByBasis afterRebate, String cess) {
        return NodeHarness.with(pack())
                .regime(NEW_TAX_REGIME)
                .given(NodeIds.TAX_AFTERREBATE, afterRebate)
                .given(NodeIds.TAX_CESS, Money.of(cess))
                .run(new NetPayableNode());
    }

    @ParameterizedTest(name = "{0}: {1} + cess {2} → {3}")
    @CsvSource({
        // DR 08 T1-N1: "tax after rebate = 0", "RATE-CESS-001: 4% × 0 = 0"
        "T1-N1, 0, 0, 0",
        // DR 08 T1-N3: "total = 52,000"
        "T1-N3, 50000, 2000, 52000",
        // DR 08 T1-N2: "total = 10.40"
        "T1-N2, 10.00, 0.40, 10.40",
        // DR 08 T1-N4: "total = 73,403.20"
        "T1-N4a, 70580.00, 2823.20, 73403.20",
        // DR 08 T1-N4: "total = 73,412.04"
        "T1-N4b, 70588.50, 2823.54, 73412.04",
        // DR 08 T1-O2: "total = 13,002.08"
        "T1-O2, 12502, 500.08, 13002.08",
        // DR 08 T2-1: "total = 11,23,200"
        "T2-1, 1080000, 43200, 1123200"
    })
    void evaluate_whenTheGoldenCaseIsRun_addsTheCessToTheTaxAfterRebate(
            String golden, String afterRebate, String cess, String netPayable) {
        // When
        NodeResult result = run(TaxByBasis.atSlabRatesOnly(Money.of(afterRebate)), cess);

        // Then
        assertThat(result.value()).as(golden).isEqualTo(MoneyValue.of(Money.of(netPayable)));
    }

    @Test
    void evaluate_whenTaxIsPartlyAtSpecialRates_addsTheWholeIncomeTax() {
        // Given — by hand: (0 + 1,00,000 special) + 4,000 cess = 1,04,000
        TaxByBasis afterRebate = new TaxByBasis(Money.ZERO, Map.of(new SpecialRateId("LTCG"), Money.of("100000")));

        // When
        NodeResult result = run(afterRebate, "4000");

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("104000")));
    }

    @Test
    void evaluate_whenRun_emitsNoLineOfItsOwn() {
        // Given — a sum of figures that already have lines; no rule states it (DR 15 "—", D-080)
        // When
        NodeResult result = run(TaxByBasis.atSlabRatesOnly(Money.of("50000")), "2000");

        // Then
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void requires_isTheTaxAfterRebateAndTheCess() {
        // When / Then
        assertThat(new NetPayableNode().requires())
                .containsExactlyInAnyOrder(NodeIds.TAX_AFTERREBATE, NodeIds.TAX_CESS);
    }

    @Test
    void run_whenTheCessIsNotGiven_isRefusedBecauseTheNodeDeclaresIt() {
        // When / Then
        assertThatThrownBy(() -> NodeHarness.with(pack())
                        .regime(NEW_TAX_REGIME)
                        .given(NodeIds.TAX_AFTERREBATE, TaxByBasis.atSlabRatesOnly(Money.of("50000")))
                        .run(new NetPayableNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.TAX_CESS.value());
    }
}
