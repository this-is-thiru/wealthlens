package com.thiru.wealthlens.taxengine.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.InputsFingerprint;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Which slot is which field (D-081). The figures are arbitrary markers, and each pair of candidate
 * slots holds different values, so a projection reading the wrong one of a pair cannot pass by
 * coincidence.
 */
class ComputationProjectorTest {

    private static final ComputationId ID = new ComputationId("00000000-0000-4000-8000-000000000001");

    private static final Money TOTAL_INCOME_UNROUNDED = Money.of("1200004.60");
    private static final Money TOTAL_INCOME_ROUNDED = Money.of("1200000");
    private static final TaxByBasis TAX_BEFORE_REBATE = TaxByBasis.atSlabRatesOnly(Money.of("60000"));
    private static final TaxByBasis TAX_AFTER_REBATE = TaxByBasis.atSlabRatesOnly(Money.of("0"));
    private static final Money NET_PAYABLE_UNROUNDED = Money.of("52001.40");
    private static final Money NET_PAYABLE_ROUNDED = Money.of("52000");

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                "Income-tax Act 2025",
                LocalDate.of(2026, 9, 25),
                VerificationLevel.SOURCE_CHECKED,
                null);
    }

    private static Map<RulePackLayer, RulePackRef> lawAndExec() {
        Map<RulePackLayer, RulePackRef> layers = new EnumMap<>(RulePackLayer.class);
        for (RulePackLayer layer : RulePackLayer.values()) {
            if (layer.affectsComputation()) {
                String name = layer.name().toLowerCase(Locale.ROOT);
                layers.put(layer, new RulePackRef(layer, "2026-27." + name + ".1", "sha256:" + "a".repeat(64)));
            }
        }
        return layers;
    }

    private static List<TraceLine> trace() {
        return List.of(TraceLine.of(1, NodeIds.TAX_BYBASIS, new RuleId("RATE-SLAB-NEW-TAX-REGIME"), StepKind.TAX,
                MoneyValue.of(TOTAL_INCOME_ROUNDED), TAX_BEFORE_REBATE, provenance()));
    }

    private static Map<NodeId, NodeValue> values() {
        Map<NodeId, NodeValue> values = new LinkedHashMap<>();
        values.put(NodeIds.TOTALINCOME, MoneyValue.of(TOTAL_INCOME_UNROUNDED));
        values.put(NodeIds.ROUND_TOTALINCOME, MoneyValue.of(TOTAL_INCOME_ROUNDED));
        values.put(NodeIds.TAX_BYBASIS, TAX_BEFORE_REBATE);
        values.put(NodeIds.TAX_AFTERREBATE, TAX_AFTER_REBATE);
        values.put(NodeIds.TAX_NETPAYABLE, MoneyValue.of(NET_PAYABLE_UNROUNDED));
        values.put(NodeIds.ROUND_NETPAYABLE, MoneyValue.of(NET_PAYABLE_ROUNDED));
        return values;
    }

    private static PipelineRunner.Result run(Map<NodeId, NodeValue> values, EngineInputs inputs) {
        return new PipelineRunner.Result(
                List.copyOf(values.keySet()), values, trace(), List.of(), InputsFingerprint.of(inputs), lawAndExec());
    }

    @Test
    void project_whenEveryAnchorIsPresent_carriesTheRunAndTheInputs() {
        // Given
        EngineInputs inputs = NodeHarness.plainTaxpayer();

        // When
        TaxComputation computation = ComputationProjector.project(run(values(), inputs), inputs, ID);

        // Then
        assertThat(computation.id()).isEqualTo(ID);
        assertThat(computation.taxYear()).isEqualTo(inputs.taxYear());
        assertThat(computation.regime()).isEqualTo(inputs.regime());
        assertThat(computation.inputs()).isEqualTo(InputsFingerprint.of(inputs));
        assertThat(computation.lawAndExec()).isEqualTo(lawAndExec());
        assertThat(computation.trace()).isEqualTo(trace());
    }

    @Test
    void project_whenTotalIncomeIsRead_takesTheRoundedSlot() {
        // Given — s.516: the rounded amount "shall be deemed to be the total income"
        EngineInputs inputs = NodeHarness.plainTaxpayer();

        // When
        TaxComputation computation = ComputationProjector.project(run(values(), inputs), inputs, ID);

        // Then
        assertThat(computation.totalIncome()).isEqualTo(TOTAL_INCOME_ROUNDED);
    }

    @Test
    void project_whenTaxByBasisIsRead_takesTheTaxBeforeRebate() {
        // Given
        EngineInputs inputs = NodeHarness.plainTaxpayer();

        // When
        TaxComputation computation = ComputationProjector.project(run(values(), inputs), inputs, ID);

        // Then
        assertThat(computation.taxByBasis()).isEqualTo(TAX_BEFORE_REBATE);
    }

    @Test
    void project_whenNetPayableIsRead_takesTheUnroundedAmountBeforeFilingEffects() {
        // Given — round.netpayable follows the date-dependent interest; no third rounding point
        EngineInputs inputs = NodeHarness.plainTaxpayer();

        // When
        TaxComputation computation = ComputationProjector.project(run(values(), inputs), inputs, ID);

        // Then
        assertThat(computation.netPayableBeforeFilingEffects()).isEqualTo(NET_PAYABLE_UNROUNDED);
    }

    @Test
    void project_whenAnAnchorIsMissing_isRefusedNamingIt() {
        // Given
        EngineInputs inputs = NodeHarness.plainTaxpayer();
        Map<NodeId, NodeValue> values = values();
        values.remove(NodeIds.TAX_NETPAYABLE);

        // When / Then
        assertThatThrownBy(() -> ComputationProjector.project(run(values, inputs), inputs, ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.TAX_NETPAYABLE.value());
    }

    @Test
    void project_whenAnAnchorHoldsTheWrongKindOfValue_isRefusedNamingIt() {
        // Given — the total income slot holding a tax split
        EngineInputs inputs = NodeHarness.plainTaxpayer();
        Map<NodeId, NodeValue> values = values();
        values.put(NodeIds.ROUND_TOTALINCOME, TAX_BEFORE_REBATE);

        // When / Then
        assertThatThrownBy(() -> ComputationProjector.project(run(values, inputs), inputs, ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.ROUND_TOTALINCOME.value())
                .hasMessageContaining("MoneyValue")
                .hasMessageContaining("TaxByBasis");
    }

    @Test
    void project_whenTheInputsAreNotTheRunsInputs_isRefused() {
        // Given — the run computed the new regime; the caller claims the other
        EngineInputs computed = NodeHarness.plainTaxpayer();
        EngineInputs claimed = new EngineInputs(computed.taxYear(), new RegimeCode("FIRST_SCHEDULE_RATES"),
                computed.temporal(), computed.entered(), computed.assessee(), computed.family(), computed.ledgers(),
                computed.elections(), computed.capitalGains(), computed.filing());

        // When / Then
        assertThatThrownBy(() -> ComputationProjector.project(run(values(), computed), claimed, ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fingerprint");
    }
}
