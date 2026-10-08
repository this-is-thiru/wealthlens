package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.AgniveerCorpusFund;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
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
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** s.125 through {@link ChapterEightNode}, TY 2026-27 (S2; D-140). Figures by hand. */
class AgniveerDeductionTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final TaxYearId TY_2026 = TaxYearId.of(2026);

    private static NodeResult run(RegimeCode regime, String own, String government) {
        return run(TY_2026, regime, own, government);
    }

    private static NodeResult run(TaxYearId year, RegimeCode regime, String own, String government) {
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(year), new JsonPackAssembler(LockedInstruments.of(year)))
                .load(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(year, regime,
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(),
                        Optional.of(new AgniveerCorpusFund(Money.of(own), Money.of(government))), Optional.empty(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs)
                .given(NodeIds.GTI, Money.of("1000000"))
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of()))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
                .run(new ChapterEightNode());
    }

    @Test
    void evaluate_whenAnAgniveerSubscribesInTaxYear2025_deductsUnderSection80cch() {
        // When — 1961 s.80CCH (D-148): 30,000 + 30,000; the new regime keeps (2) only (s.115BAC(2)(i))
        NodeResult old = run(TaxYearId.of(2025), OLD, "30000", "30000");
        NodeResult regime = run(TaxYearId.of(2025), NEW, "30000", "30000");

        // Then
        assertThat(old.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
        assertThat(regime.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
    }

    @Test
    void evaluate_whenBothPayInTheOldRegime_deductsTheWholeOfEach() {
        // When — s.125(1), (2): 30,000 + 30,000
        NodeResult result = run(OLD, "30000", "30000");

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.DED_AGNIVEER_OWN, RuleIds.DED_AGNIVEER_GOVERNMENT);
    }

    @Test
    void evaluate_whenBothPayInTheNewRegime_deductsOnlyTheGovernmentsContribution() {
        // When — s.202(2)(a)(xii) keeps s.125(2), not (1)
        NodeResult result = run(NEW, "30000", "30000");

        // Then — 30,000, and a nil line for the Agniveer's own
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.DED_AGNIVEER_OWN))
                .singleElement().satisfies(line -> assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO)));
    }
}
