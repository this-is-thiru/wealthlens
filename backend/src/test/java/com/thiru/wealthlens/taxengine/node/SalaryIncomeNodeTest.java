package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Income under "Salaries" (S2 s.19(1): "computed after making the deductions"; D-096): gross salary less the
 * deductions from salary, untraced because both already carry their lines. The amounts are fixtures; each
 * difference is worked by hand.
 */
class SalaryIncomeNodeTest {

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static NodeResult run(int year, String gross, String deductions) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, FIRST_SCHEDULE_RATES,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)), plain.entered(), plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack)
                .regime(FIRST_SCHEDULE_RATES)
                .inputs(inputs)
                .given(NodeIds.SALARY_GROSS, MoneyValue.of(Money.of(gross)))
                .given(NodeIds.SALARY_DEDUCTIONS, MoneyValue.of(Money.of(deductions)))
                .run(new SalaryIncomeNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenDeductionsAreBelowGross_isGrossLessDeductions(int year) {
        // When
        NodeResult result = run(year, "900000", "52500");

        // Then — 9,00,000 − 52,500 = 8,47,500, with no line of its own
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("847500")));
        assertThat(result.trace()).isEmpty();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenNothingIsEntered_isNil(int year) {
        // When
        NodeResult result = run(year, "0", "0");

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenDeductionsExceedGross_isRefusedNamingBoth(int year) {
        // When / Then — 1,000 of salary against 2,500 of professional tax: the Act gives no loss under "Salaries"
        assertThatThrownBy(() -> run(year, "1000", "3500"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1000")
                .hasMessageContaining("3500");
    }
}
