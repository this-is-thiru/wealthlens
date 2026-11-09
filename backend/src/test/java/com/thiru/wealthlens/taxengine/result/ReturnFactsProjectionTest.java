package com.thiru.wealthlens.taxengine.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.InputsFingerprint;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.node.ShippedNodes;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.ReturnFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A run's {@link ReturnFacts} (W-090c1b1): the four heads before set-off, the capital-gains parts, and the rows whose
 * {@code DEDUCTION} line allowed more than nil. A hand-built run fixes every figure; one run of the whole shipped
 * engine shows a real deduction line read the same way.
 */
class ReturnFactsProjectionTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final CapitalGainsPart LONG_LISTED = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");
    private static final RuleId COMMISSION = new RuleId("OS-BANKER-COMMISSION");
    private static final RuleId EXPENDITURE = new RuleId("OS-OTHER-EXPENDITURE");
    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-090c1b1"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 9), VerificationLevel.UNVERIFIED, "Nothing: synthetic");

    private static TraceLine deduction(int sequence, RuleId row, String claimed, String allowed) {
        return TraceLine.of(sequence, NodeIds.OTHERSOURCES_INCOME, row, StepKind.DEDUCTION,
                MoneyValue.of(Money.of(claimed)), MoneyValue.of(Money.of(allowed)), SOURCE);
    }

    private static PipelineRunner.Result handBuilt() {
        Map<NodeId, NodeValue> values = new LinkedHashMap<>();
        values.put(NodeIds.SALARY_INCOME, MoneyValue.of(Money.of("900000")));
        values.put(NodeIds.HOUSEPROPERTY_INCOME, MoneyValue.of(Money.of("-20000")));
        values.put(NodeIds.OTHERSOURCES_INCOME, MoneyValue.of(Money.of("48000")));
        values.put(NodeIds.CAPITALGAINS_INCOME, new CapitalGainsHead(Map.of(LONG_LISTED, Money.of("125000")), Map.of(),
                Money.ZERO, Money.ZERO, List.of()));
        // A banker's commission of ₹2,000 allowed in full; ₹1,000 of expenditure refused (a nil line)
        List<TraceLine> trace = List.of(deduction(1, COMMISSION, "2000", "2000"), deduction(2, EXPENDITURE, "1000", "0"));
        return new PipelineRunner.Result(List.copyOf(values.keySet()), values, trace, List.of(),
                InputsFingerprint.of(NodeHarness.plainTaxpayer()), Map.of());
    }

    @Test
    void of_whenTheRunHoldsEveryHead_carriesEachBeforeSetOff() {
        // Given / When
        ReturnFacts facts = ReturnFactsProjection.of(handBuilt());

        // Then
        assertThat(facts.incomeUnder(HeadCode.SALARIES)).isEqualTo(Money.of("900000"));
        assertThat(facts.incomeUnder(HeadCode.HOUSE_PROPERTY)).isEqualTo(Money.of("-20000"));
        assertThat(facts.incomeUnder(HeadCode.OTHER_SOURCES)).isEqualTo(Money.of("48000"));
        assertThat(facts.incomeUnder(HeadCode.CAPITAL_GAINS)).isEqualTo(Money.of("125000"));
        assertThat(facts.capitalGains().get(LONG_LISTED)).isEqualTo(Money.of("125000"));
    }

    @Test
    void of_whenOneDeductionIsAllowedAndOneRefused_listsTheAllowedRowOnly() {
        // Given / When
        ReturnFacts facts = ReturnFactsProjection.of(handBuilt());

        // Then
        assertThat(facts.allowedDeductionUnder(COMMISSION)).isTrue();
        assertThat(facts.allowedDeductionUnder(EXPENDITURE)).isFalse();
        assertThat(facts.deductionsAllowed()).containsExactly(COMMISSION);
    }

    @Test
    void of_whenTheShippedEngineDeductsAFamilyPensionsShare_listsItsRow() {
        // Given — ₹3,00,000 of family pension in the new regime: s.93(1)(d)(i)'s share is deducted
        RulePack pack = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        OtherSourceItem pension = new OtherSourceItem(OtherSourceKind.FAMILY_PENSION, Money.of("300000"), Money.ZERO,
                Money.ZERO, Money.ZERO, false);
        EngineInputs inputs = new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(pension), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());

        // When
        ReturnFacts facts = ReturnFactsProjection.of(PipelineRunner.run(NodeRegistry.of(ShippedNodes.all()), pack,
                pack.regimes().get(new RegimeCode("NEW_TAX_REGIME")), inputs));

        // Then
        assertThat(facts.deductionsAllowed()).containsExactly(new RuleId("OS-FAMILY-PENSION-NEW-TAX-REGIME"));
        assertThat(facts.incomeUnder(HeadCode.OTHER_SOURCES).isGreaterThan(Money.ZERO)).isTrue();
    }
}
