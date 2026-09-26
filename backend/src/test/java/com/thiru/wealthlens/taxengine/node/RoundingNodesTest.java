package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.rounding.RuleRounding;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The two rounding points of s.516 (D-076). Payable figures are copied from {@code DR 08} and
 * {@code DR 13}; total-income figures are worked by hand from s.516's text in {@code plans/W-050.md},
 * because every {@code DR 08} total income is already a multiple of ten.
 */
class RoundingNodesTest {

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

    private static NodeResult run(RulePack pack, ComputationNode node, NodeId input, String amount) {
        return NodeHarness.with(pack)
                .regime(NEW_TAX_REGIME)
                .given(input, Money.of(amount))
                .run(node);
    }

    private static NodeResult roundTotalIncome(String totalIncome) {
        return run(pack(), new RoundTotalIncomeNode(), NodeIds.TOTALINCOME, totalIncome);
    }

    private static NodeResult roundPayable(String payable) {
        return run(pack(), new RoundNetPayableNode(), NodeIds.TAX_NETPAYABLE, payable);
    }

    // -- total income ------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        // paise ignored → 12,00,004; last figure 4 < 5 → down. Rupee-first rounding would give 12,00,010
        "1200004.60, 1200000",
        // last figure 5 ≥ 5 → up
        "1200005, 1200010",
        // already a multiple of ten
        "1200000, 1200000"
    })
    void evaluate_whenTotalIncomeIsRounded_followsSection516(String totalIncome, String rounded) {
        // When
        NodeResult result = roundTotalIncome(totalIncome);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(rounded)));
    }

    @Test
    void evaluate_whenTotalIncomeIsRounded_emitsOneRoundingLineCitingItsRow() {
        // When
        NodeResult result = roundTotalIncome("1200000");

        // Then — the line is emitted even when nothing moved, so the step is always visible
        TraceLine line = result.trace().getFirst();
        assertThat(result.trace()).hasSize(1);
        assertThat(line.kind()).isEqualTo(StepKind.ROUNDING);
        assertThat(line.rule()).isEqualTo(RuleIds.RATE_ROUND_TOTAL_INCOME);
        assertThat(line.node()).isEqualTo(NodeIds.ROUND_TOTALINCOME);
        assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("1200000")));
    }

    // -- amount payable or refundable ----------------------------------------------------------

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        // DR 08 T1-N2: "RATE-ROUND-002: ignore paise → 10; already a multiple of 10"
        "10.40, 10",
        // DR 08 T1-N4: "round: → 73,403 → last figure 3 < 5 → down"
        "73403.20, 73400",
        // DR 08 T1-N4: "round: → 73,412 → last figure 2 < 5 → down"
        "73412.04, 73410",
        // DR 08 T1-O2: "round: → 13,002 → last figure 2 < 5 → down"
        "13002.08, 13000",
        // DR 08 T2-2: "round: → 12,90,837 → last figure 7 ≥ 5 → up"
        "1290837.60, 1290840",
        // DR 08 T2-2: "round: → 12,90,843 → last figure 3 < 5 → down"
        "1290843.84, 1290840"
    })
    void evaluate_whenPayableIsRounded_matchesTheGoldenCases(String payable, String rounded) {
        // When
        NodeResult result = roundPayable(payable);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(rounded)));
    }

    @Test
    void evaluate_whenARefundIsRounded_roundsTheMagnitudeAndKeepsTheSign() {
        // Given — DR 13 T13-14: "Paise ignored → 1,234 → last figure 4 → ₹1,230"; the negative-sign
        // variant is ROUND-NEG-001, recorded STATUS UNCLEAR, and this pins that reading
        // When
        NodeResult result = roundPayable("-1234.99");

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("-1230")));
    }

    @Test
    void evaluate_whenPayableIsRounded_emitsOneRoundingLineCitingItsRow() {
        // When
        NodeResult result = roundPayable("10.40");

        // Then
        TraceLine line = result.trace().getFirst();
        assertThat(result.trace()).hasSize(1);
        assertThat(line.kind()).isEqualTo(StepKind.ROUNDING);
        assertThat(line.rule()).isEqualTo(RuleIds.RATE_ROUND_PAYABLE);
        assertThat(line.node()).isEqualTo(NodeIds.ROUND_NETPAYABLE);
        assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("10")));
    }

    // -- what the nodes accept -------------------------------------------------------------------

    @Test
    void evaluate_whenTheRowRoundsToAnythingButTen_isRefused() {
        // Given — the Acts state one method, s.516's; a pack asking for another is not guessed at
        RulePack pack = pack();
        RuleRow row = pack.rule(RuleIds.RATE_ROUND_TOTAL_INCOME);
        RuleVersion v = row.versions().getFirst();
        RuleVersion toHundred = new RuleVersion(v.validity(), v.regimes(), v.cap(), v.relief(),
                Optional.of(new RuleRounding(Money.of("100"))), v.definition(), v.conditions(), v.provision1961(),
                v.provision2025(), v.introducedBy(), v.legalStatus(), v.publicationStatus(), v.provenance());
        Map<RuleId, RuleRow> rules = new HashMap<>(pack.rules());
        rules.put(row.id(), new RuleRow(row.id(), row.axis(), List.of(toHundred),
                row.midYearCombination(), row.midYearCombinationRule(), row.headProvenance()));
        RulePack hundred = new RulePack(pack.taxYear(), pack.layers(), pack.instruments(), rules,
                pack.components(), pack.regimes(), pack.disallowanceSets(), pack.conditionSets());

        // When / Then
        assertThatThrownBy(() -> run(hundred, new RoundTotalIncomeNode(), NodeIds.TOTALINCOME, "1200000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RuleIds.RATE_ROUND_TOTAL_INCOME.value())
                .hasMessageContaining("100");
    }

    @Test
    void run_whenTheNetPayableIsNotGiven_isRefusedBecauseTheNodeDeclaresIt() {
        // When / Then
        assertThatThrownBy(() -> NodeHarness.with(pack()).regime(NEW_TAX_REGIME).run(new RoundNetPayableNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.TAX_NETPAYABLE.value());
    }
}
