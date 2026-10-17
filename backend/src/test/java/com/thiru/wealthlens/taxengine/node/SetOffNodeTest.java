package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Set-off of a head's loss against the other heads in the year (S2 s.109; 1961 s.71; D-217). Fixture amounts, each
 * figure worked by hand; the ₹2,00,000 is S2 s.109(1)(b) / 1961 s.71(3A).
 */
class SetOffNodeTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    private static NodeResult run(int year, RegimeCode regime, String salaries, String houseProperty,
            String otherSources) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)), plain.entered(), plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack(year)).regime(regime).inputs(inputs)
                .given(NodeIds.SALARY_INCOME, Money.of(salaries))
                .given(NodeIds.HOUSEPROPERTY_INCOME, Money.of(houseProperty))
                .given(NodeIds.OTHERSOURCES_INCOME, Money.of(otherSources))
                .run(new SetOffNode());
    }

    private static MoneyValue rupees(String amount) {
        return MoneyValue.of(Money.of(amount));
    }

    private static List<TraceLine> lines(NodeResult result, StepKind kind) {
        return result.trace().stream().filter(line -> line.kind() == kind).toList();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAHousePropertyLossIsUnderTheLimit_setsItOffWhole(int year) {
        // When — 10,00,000 salary, a 1,50,000 house-property loss
        NodeResult result = run(year, OLD, "1000000", "-150000", "0");

        // Then — 10,00,000 − 1,50,000
        assertThat(result.value()).isEqualTo(rupees("850000"));
        assertThat(lines(result, StepKind.SET_OFF)).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SETOFF_HOUSE_PROPERTY);
            assertThat(line.in()).isEqualTo(rupees("1000000"));
            assertThat(line.out()).isEqualTo(rupees("150000"));
            assertThat(line.limitedBy().orElseThrow()).contains("HOUSE_PROPERTY loss against SALARIES")
                    .contains("[SALARIES, HOUSE_PROPERTY, OTHER_SOURCES]").contains("OQ-28");
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAHousePropertyLossExceedsTheLimit_setsOffTwoLakhAndCarriesTheRest(int year) {
        // When — a 3,00,000 loss against 10,00,000 salary
        NodeResult result = run(year, OLD, "1000000", "-300000", "0");

        // Then — 2,00,000 set off, 10,00,000 − 2,00,000; 1,00,000 left to carry forward (W-081b)
        assertThat(result.value()).isEqualTo(rupees("800000"));
        assertThat(lines(result, StepKind.SET_OFF)).extracting(TraceLine::out).containsExactly(rupees("200000"));
        assertThat(lines(result, StepKind.MEASURE)).extracting(TraceLine::in, TraceLine::out)
                .containsExactly(tuple(rupees("300000"), rupees("200000")), tuple(rupees("300000"), rupees("100000")));
        assertThat(lines(result, StepKind.MEASURE).get(1).limitedBy().orElseThrow()).contains("carried forward")
                .contains("W-081b");
    }

    @Test
    void evaluate_whenTheOtherHeadsHoldLessThanTheLoss_setsOffWhatTheyHold() {
        // When — a 1,50,000 loss against 50,000 salary
        NodeResult result = run(2026, OLD, "50000", "-150000", "0");

        // Then — 50,000 set off, nil left under the heads, 1,00,000 to carry forward
        assertThat(result.value()).isEqualTo(rupees("0"));
        assertThat(lines(result, StepKind.SET_OFF)).extracting(TraceLine::out).containsExactly(rupees("50000"));
        assertThat(lines(result, StepKind.MEASURE).get(1).out()).isEqualTo(rupees("100000"));
    }

    @Test
    void evaluate_whenTwoHeadsCanAbsorbTheLoss_takesSalariesFirstThenOtherSources() {
        // When — a 1,50,000 loss against 1,00,000 salary and 2,00,000 under other sources
        NodeResult result = run(2026, OLD, "100000", "-150000", "200000");

        // Then — the declared order: 1,00,000 from salaries, then 50,000 from other sources; 1,50,000 left
        assertThat(result.value()).isEqualTo(rupees("150000"));
        assertThat(lines(result, StepKind.SET_OFF)).extracting(TraceLine::in, TraceLine::out)
                .containsExactly(tuple(rupees("100000"), rupees("100000")), tuple(rupees("200000"), rupees("50000")));
        assertThat(lines(result, StepKind.SET_OFF).get(1).limitedBy().orElseThrow())
                .contains("HOUSE_PROPERTY loss against OTHER_SOURCES");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheNewRegimeHasAHousePropertyLoss_setsOffNothingAndTheLossIsGone(int year) {
        // When — a 40,000 house-property loss against 10,00,000 salary
        NodeResult result = run(year, NEW, "1000000", "-40000", "0");

        // Then — nothing set off; the loss deemed given full effect to (S2 s.202(2)(b)(ii), (3); 1961 s.115BAC)
        assertThat(result.value()).isEqualTo(rupees("1000000"));
        assertThat(lines(result, StepKind.SET_OFF)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(rupees("0"));
            assertThat(line.limitedBy().orElseThrow()).contains("REG-NEW-TAX-REGIME-NO-SETOFF");
        });
        assertThat(lines(result, StepKind.MEASURE)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(rupees("40000"));
            assertThat(line.limitedBy().orElseThrow()).contains("deemed to have been given full effect to");
        });
    }

    @Test
    void evaluate_whenOtherSourcesHasALoss_setsItOffWithoutALimit() {
        // When — a 3,00,000 loss under other sources against 10,00,000 salary
        NodeResult result = run(2026, OLD, "1000000", "0", "-300000");

        // Then — the whole 3,00,000 (s.109(1) sets no limit for this head)
        assertThat(result.value()).isEqualTo(rupees("700000"));
        assertThat(lines(result, StepKind.SET_OFF)).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SETOFF_ACROSS_HEADS);
            assertThat(line.out()).isEqualTo(rupees("300000"));
        });
        assertThat(lines(result, StepKind.MEASURE)).isEmpty();
    }

    @Test
    void evaluate_whenAnOtherSourcesLossExceedsTheOtherHeads_isNotCarried() {
        // When — a 1,50,000 loss under other sources against 1,00,000 salary
        NodeResult result = run(2026, OLD, "100000", "0", "-150000");

        // Then — 1,00,000 set off; 50,000 left, which no provision carries forward
        assertThat(result.value()).isEqualTo(rupees("0"));
        assertThat(lines(result, StepKind.MEASURE)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(rupees("50000"));
            assertThat(line.limitedBy().orElseThrow()).contains("not carried forward");
        });
    }
}
