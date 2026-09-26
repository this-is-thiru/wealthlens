package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The Health and Education Cess, 4% of the income-tax after rebate (FA 2026 s.3(15); D-079). Every figure
 * is copied from {@code DR 08}, except the special-rate case, worked by hand in {@code plans/W-051.md}.
 */
class CessNodeTest {

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

    private static NodeResult run(RulePack pack, TaxByBasis afterRebate, String surcharge) {
        return NodeHarness.with(pack)
                .regime(NEW_TAX_REGIME)
                .given(NodeIds.TAX_AFTERREBATE, afterRebate)
                .given(NodeIds.TAX_SURCHARGE, Money.of(surcharge))
                .run(new CessNode());
    }

    private static NodeResult run(RulePack pack, TaxByBasis afterRebate) {
        return run(pack, afterRebate, "0");
    }

    private static NodeResult run(TaxByBasis afterRebate) {
        return run(pack(), afterRebate);
    }

    private static TaxByBasis slab(String tax) {
        return TaxByBasis.atSlabRatesOnly(Money.of(tax));
    }

    @ParameterizedTest(name = "{0}: {1} after rebate → cess {2}")
    @CsvSource({
        // DR 08 T2-1: "cess 4% × 10,80,000 = 43,200" (no rebate, surcharge 0 at the threshold)
        "T2-1, 1080000, 43200",
        // DR 08 T1-N1: "RATE-CESS-001: 4% × 0 = 0"
        "T1-N1, 0, 0",
        // DR 08 T1-N3: "cess 4% = 2,000"
        "T1-N3, 50000, 2000",
        // DR 08 T1-N4: "cess 4% = 2,823.20"
        "T1-N4a, 70580.00, 2823.20",
        // DR 08 T1-N4: "cess 4% = 2,823.54"
        "T1-N4b, 70588.50, 2823.54",
        // DR 08 T1-O2: "cess 4% = 500.08"
        "T1-O2, 12502, 500.08"
    })
    void evaluate_whenTheGoldenCaseIsRun_chargesFourPercentOfTheTaxAfterRebate(
            String golden, String afterRebate, String cess) {
        // When
        NodeResult result = run(slab(afterRebate));

        // Then
        assertThat(result.value()).as(golden).isEqualTo(MoneyValue.of(Money.of(cess)));
    }

    @ParameterizedTest(name = "{0}: {1} + surcharge {2} → cess {3}")
    @CsvSource({
        // DR 08 T2-2: "tax + surcharge after relief = 10,80,010", "cess 4% × 10,80,010 = 43,200.40"
        "T2-2, 1080003, 7.00, 43200.40",
        // DR 08 T2-4 old: "cess 4% × 1,85,15,625 = 7,40,625"
        "T2-4, 14812500, 3703125, 740625",
        // DR 08 T2-5: "tax + surcharge after relief = 1,85,15,635", "cess 4% = 7,40,625.40"
        "T2-5, 14812503, 3703132.00, 740625.40"
    })
    void evaluate_whenASurchargeIsDue_chargesFourPercentOfTaxAndSurcharge(
            String golden, String afterRebate, String surcharge, String cess) {
        // When — FA 2026 s.3(15): "such income-tax and surcharge"
        NodeResult result = run(pack(), slab(afterRebate), surcharge);

        // Then
        assertThat(result.value()).as(golden).isEqualTo(MoneyValue.of(Money.of(cess)));
    }

    @Test
    void evaluate_whenT1N2_chargesTheCessOnTheTaxAfterRebate() {
        // Given — DR 08 T1-N2: "tax after rebate = 10.00", "cess 4% × 10 = 0.40"; charged before the
        // rebate it would be 4% × 60,001.50 = 2,400.06
        // When
        NodeResult result = run(slab("10.00"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("0.40")));
    }

    @Test
    void requires_isTheTaxAfterRebateNeverTheTaxBeforeIt() {
        // Given — REBATE-ORDER-001: "slab tax → less rebate → plus surcharge → plus cess". The node can
        // read only what it declares, so declaring the post-rebate tax and the surcharge, and never
        // tax.bybasis, is what keeps the order
        // When
        Set<NodeId> requires = new CessNode().requires();

        // Then
        assertThat(requires).containsExactlyInAnyOrder(NodeIds.TAX_AFTERREBATE, NodeIds.TAX_SURCHARGE);
    }

    @Test
    void evaluate_whenTaxIsPartlyAtSpecialRates_chargesTheWholeIncomeTax() {
        // Given — by hand: 4% × (0 + 1,00,000) = 4,000; s.3(15) charges "such income-tax" whole
        TaxByBasis tax = new TaxByBasis(Money.ZERO, Map.of(new SpecialRateId("LTCG"), Money.of("100000")));

        // When
        NodeResult result = run(tax);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("4000")));
    }

    @Test
    void evaluate_whenRun_explainsItselfInOneTaxLineCitingTheCessRow() {
        // When
        NodeResult result = run(slab("50000"));

        // Then
        TraceLine line = result.trace().getFirst();
        assertThat(result.trace()).hasSize(1);
        assertThat(line.kind()).isEqualTo(StepKind.TAX);
        assertThat(line.rule()).isEqualTo(RuleIds.RATE_HEALTH_EDUCATION_CESS);
        assertThat(line.node()).isEqualTo(NodeIds.TAX_CESS);
        assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("50000")));
        assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("2000")));
    }

    @Test
    void evaluate_whenTheCessRowIsNotAPercentage_isRefused() {
        // Given — the row rewritten to a Fixed amount
        RulePack pack = pack();
        RuleRow row = pack.rule(RuleIds.RATE_HEALTH_EDUCATION_CESS);
        RuleVersion v = row.versions().getFirst();
        RuleVersion fixed = new RuleVersion(v.validity(), v.regimes(), new Fixed(Money.of("100")), v.relief(),
                v.rounding(), v.conditions(), v.provision1961(), v.provision2025(), v.introducedBy(),
                v.legalStatus(), v.publicationStatus(), v.provenance());
        Map<RuleId, RuleRow> rules = new HashMap<>(pack.rules());
        rules.put(row.id(), new RuleRow(row.id(), row.axis(), List.of(fixed),
                row.midYearCombination(), row.midYearCombinationRule(), row.headProvenance()));
        RulePack notAPercentage = new RulePack(pack.taxYear(), pack.layers(), pack.instruments(), rules,
                pack.components(), pack.regimes(), pack.disallowanceSets(), pack.conditionSets());

        // When / Then
        assertThatThrownBy(() -> run(notAPercentage, slab("50000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RuleIds.RATE_HEALTH_EDUCATION_CESS.value());
    }

    @Test
    void run_whenTheTaxAfterRebateIsNotGiven_isRefusedBecauseTheNodeDeclaresIt() {
        // When / Then
        assertThatThrownBy(() -> NodeHarness.with(pack()).regime(NEW_TAX_REGIME).run(new CessNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.TAX_AFTERREBATE.value());
    }
}
