package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.component.ComponentLifecycle;
import com.thiru.wealthlens.taxengine.rules.component.DeprecationPolicy;
import com.thiru.wealthlens.taxengine.rules.component.TaxTreatment;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The deductions from salary (S2 s.19(1) Table Sl. 1–2; 1961 s.16(ia), (iii); D-093): professional tax in full
 * outside the new regime, and the standard deduction — the regime's amount or the salary, whichever is less.
 * Professional tax is the shipped component; the pay line is a fixture. Each expected figure is worked by hand
 * from the statutory amounts, ₹75,000 (Sl. 2(a)) and ₹50,000 (Sl. 2(b)).
 */
class SalaryDeductionNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final ComponentCode BASIC = new ComponentCode("FIXTURE_BASIC");

    private static RulePack withFixturePay(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack shipped = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        Map<ComponentCode, ComponentDefinition> registry = new HashMap<>(shipped.components());
        registry.put(BASIC, new ComponentDefinition(BASIC, "Fixture basic", HeadCode.SALARIES, ComponentKind.SALARY,
                CashNature.CASH, TaxTreatment.TAXABLE, Set.of(RuleIds.SAL_SALARY_INCLUDES), Set.of(), false,
                new ComponentLifecycle(DateRange.fromOnwards(LocalDate.of(2025, 4, 1)), Optional.empty(),
                        new DeprecationPolicy.HistoricalOnly()),
                shipped.rule(RuleIds.SAL_SALARY_INCLUDES).headProvenance()));
        return new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), shipped.rules(), registry,
                shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());
    }

    private static NodeResult run(int year, RegimeCode regime, String basic, String professionalTax) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)), plain.entered(), plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        List<Line> lines = professionalTax == null
                ? List.of(new Line(0, BASIC, Money.of(basic)))
                : List.of(new Line(0, BASIC, Money.of(basic)),
                        new Line(0, ComponentCodes.PROFESSIONAL_TAX, Money.of(professionalTax)));
        return NodeHarness.with(withFixturePay(year))
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of(HeadCode.SALARIES, lines)))
                .given(NodeIds.SALARY_GROSS, MoneyValue.of(Money.of(basic)))
                .run(new SalaryDeductionNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOutsideTheNewRegime_deductsProfessionalTaxAndTheSmallerStandardDeduction(int year) {
        // When
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "600000", "2500");

        // Then — 2,500 + 50,000 = 52,500
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("52500")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(
                RuleIds.SAL_TAX_ON_EMPLOYMENT, RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE);
        assertThat(result.trace()).allSatisfy(line -> assertThat(line.kind()).isEqualTo(StepKind.DEDUCTION));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenInTheNewRegime_allowsNoProfessionalTaxAndTheLargerStandardDeduction(int year) {
        // When
        NodeResult result = run(year, NEW_TAX_REGIME, "600000", "2500");

        // Then — 75,000 only: s.202(2)(a)(iv) removes the 2,500, and no line claims it
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("75000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.SAL_STANDARD_DEDUCTION_NEW_TAX_REGIME);
        assertThat(result.trace().getFirst().limitedBy()).contains("statutory amount");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheSalaryIsBelowTheStatutoryAmount_deductsNoMoreThanTheSalary(int year) {
        // When — a part-year salary of 40,000 in the new regime
        NodeResult result = run(year, NEW_TAX_REGIME, "40000", null);

        // Then — 40,000, the lesser of 75,000 and the salary; the salary is what bound
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
        assertThat(result.trace().getFirst().limitedBy()).contains("salary");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenNoProfessionalTaxIsEntered_stillExplainsItsNilLine(int year) {
        // When
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "600000", null);

        // Then — 0 + 50,000; the tax-on-employment line is there at nil
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("50000")));
        assertThat(result.trace().getFirst().out()).isEqualTo(MoneyValue.of(Money.ZERO));
    }
}
