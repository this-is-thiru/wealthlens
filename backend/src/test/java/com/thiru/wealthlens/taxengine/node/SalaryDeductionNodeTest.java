package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
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
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.MidYearCombination;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.CalendarMonths;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
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
        registry.put(BASIC, new ComponentDefinition(BASIC, "Fixture basic", Optional.of(HeadCode.SALARIES), ComponentKind.SALARY,
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
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
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

    @Test
    void evaluate_whenTaxYear2025InTheNewRegime_restsTheSeventyFiveThousandOnTheTaxationLawsAmendmentAct2025() {
        // Given — D-108: s.16(ia)'s proviso gave Rs 75,000 only under s.115BAC(1A)(ii); TY 2025-26 is computed under
        // clause (iii), which the Taxation Laws (Amendment) Act, 2025 s.3 added to the proviso from 1-4-2025
        TaxYearId taxYear = TaxYearId.of(2025);
        RuleVersion version = withFixturePay(2025).rule(RuleIds.SAL_STANDARD_DEDUCTION_NEW_TAX_REGIME)
                .versionAt(TemporalContext.forTaxYear(taxYear, LocalDate.of(2025, 9, 21)));

        // When
        NodeResult result = run(2025, NEW_TAX_REGIME, "600000", null);

        // Then
        assertThat(version.introducedBy().gazetteRef()).isEqualTo("CG-DL-E-22082025-265618");
        assertThat(version.introducedBy().retrospective()).isTrue();
        assertThat(version.introducedBy().effectiveFrom()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("75000")));
            assertThat(line.provenance().sourceUrl().toString())
                    .isEqualTo("https://egazette.gov.in/WriteReadData/2025/265618.pdf");
            assertThat(line.provenance().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
        });
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

    @Test
    void evaluate_whenTheStandardDeductionChangesMidYearProratedByCalendarMonths_appliesEachForItsMonths() {
        // Given — D-098: a fixture change to the old-regime amount on 1 Oct 2026, from 50,000 to 60,000
        RulePack shipped = withFixturePay(2026);
        RuleRow row = shipped.rule(RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE);
        RuleVersion v = row.versions().getFirst();
        RuleVersion first = new RuleVersion(
                new Validity.Between(LocalDate.of(2026, 4, 1), Optional.of(LocalDate.of(2026, 9, 30))), v.regimes(),
                new Fixed(Money.of("50000")), v.relief(), v.rounding(), v.definition(), v.conditions(),
                v.provision1961(), v.provision2025(), v.introducedBy(), v.legalStatus(), v.publicationStatus(),
                v.provenance());
        RuleVersion second = new RuleVersion(Validity.Between.fromOnwards(LocalDate.of(2026, 10, 1)), v.regimes(),
                new Fixed(Money.of("60000")), v.relief(), v.rounding(), v.definition(), v.conditions(),
                v.provision1961(), v.provision2025(), v.introducedBy(), v.legalStatus(), v.publicationStatus(),
                v.provenance());
        Map<RuleId, RuleRow> rules = new HashMap<>(shipped.rules());
        rules.put(row.id(), new RuleRow(row.id(), row.axis(), List.of(first, second),
                MidYearCombination.PRORATE_PER_SEGMENT, null, Optional.of(new CalendarMonths()),
                row.headProvenance()));
        RulePack split = new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), rules,
                shipped.components(), shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        TaxYearId taxYear = TaxYearId.of(2026);
        EngineInputs inputs = new EngineInputs(taxYear, FIRST_SCHEDULE_RATES,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(2026, 9, 21)), plain.entered(), plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());

        // When
        NodeResult result = NodeHarness.with(split)
                .regime(FIRST_SCHEDULE_RATES)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of(HeadCode.SALARIES,
                        List.of(new Line(0, BASIC, Money.of("600000"))))))
                .given(NodeIds.SALARY_GROSS, MoneyValue.of(Money.of("600000")))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
                .run(new SalaryDeductionNode());

        // Then — 50,000 × 6/12 + 60,000 × 6/12 = 25,000 + 30,000 = 55,000; year-end alone would give 60,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("55000")));
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

    private static NodeResult runEntertainment(int year, RegimeCode regime, List<EmployerKind> employers,
            String basic, String allowance) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        TaxYearId taxYear = TaxYearId.of(year);
        List<Employment> employments = employers.stream().map(kind -> new Employment(List.of(), false, kind, false, Optional.empty())).toList();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(employments, List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        List<Line> lines = new ArrayList<>();
        List<Money> bases = new ArrayList<>();
        for (int employment = 0; employment < employers.size(); employment++) {
            lines.add(new Line(employment, BASIC, Money.of(basic)));
            lines.add(new Line(employment, ComponentCodes.ENTERTAINMENT_ALLOWANCE, Money.of(allowance)));
            bases.add(Money.of(basic));
        }
        Money gross = lines.stream().map(Line::amount).reduce(Money.ZERO, Money::plus);
        NodeHarness harness = NodeHarness.with(withFixturePay(year))
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of(HeadCode.SALARIES, lines)))
                .given(NodeIds.SALARY_GROSS, MoneyValue.of(gross));
        return harness.given(NodeIds.SALARY_BASE, new SalaryBases(year == 2025
                        ? Map.of(RuleIds.SAL_SALARY_FOR_ENTMT, bases) : Map.of()))
                .run(new SalaryDeductionNode());
    }

    private static List<TraceLine> entertainment(NodeResult result) {
        return result.trace().stream().filter(line -> line.rule().equals(RuleIds.SAL_ENTMT_ALLOWANCE)).toList();
    }

    @Test
    void evaluate_whenAGovernmentEmployeeHasAnEntertainmentAllowance_deductsTheLeastOfAFifthAndFiveThousand() {
        // When — 1961 s.16(ii): least of 3,00,000 ÷ 5 = 60,000 and 5,000
        NodeResult result = runEntertainment(2025, FIRST_SCHEDULE_RATES, List.of(EmployerKind.GOVERNMENT),
                "300000", "10000");

        // Then
        assertThat(entertainment(result)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("5000")));
            assertThat(line.kind()).isEqualTo(StepKind.DEDUCTION);
            assertThat(line.limitedBy()).contains("limit");
        });
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.SAL_TAX_ON_EMPLOYMENT,
                RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE, RuleIds.SAL_ENTMT_ALLOWANCE);
    }

    @Test
    void evaluate_whenAFifthOfSalaryIsBelowTheLimit_deductsItEvenAboveTheAllowance() {
        // When — least of 20,000 ÷ 5 = 4,000 and 5,000; the Act does not cap it at the 2,000 paid (D-105)
        NodeResult result = runEntertainment(2025, FIRST_SCHEDULE_RATES, List.of(EmployerKind.GOVERNMENT),
                "20000", "2000");

        // Then
        assertThat(entertainment(result)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("4000")));
            assertThat(line.limitedBy()).contains("one-fifth of salary");
        });
    }

    @Test
    void evaluate_whenTheEmployerIsNotTheGovernment_deductsNoEntertainmentAllowance() {
        // When — "in receipt of a salary from the Government"
        NodeResult result = runEntertainment(2025, FIRST_SCHEDULE_RATES, List.of(EmployerKind.OTHER), "300000",
                "10000");

        // Then
        assertThat(entertainment(result)).isEmpty();
    }

    @Test
    void evaluate_whenNoEntertainmentAllowanceIsPaid_deductsNone() {
        // When — "in respect of any allowance … specifically granted"
        NodeResult result = runEntertainment(2025, FIRST_SCHEDULE_RATES, List.of(EmployerKind.GOVERNMENT),
                "300000", "0");

        // Then
        assertThat(entertainment(result)).isEmpty();
    }

    @Test
    void evaluate_whenInTheNewRegime_deductsNoEntertainmentAllowance() {
        // When — s.115BAC(2)(i): "clause (ii) or clause (iii) of section 16"
        NodeResult result = runEntertainment(2025, NEW_TAX_REGIME, List.of(EmployerKind.GOVERNMENT), "300000",
                "10000");

        // Then
        assertThat(entertainment(result)).isEmpty();
    }

    @Test
    void evaluate_whenTaxYear2026_deductsNoEntertainmentAllowance() {
        // When — the 2025 Act has no counterpart
        NodeResult result = runEntertainment(2026, FIRST_SCHEDULE_RATES, List.of(EmployerKind.GOVERNMENT),
                "300000", "10000");

        // Then
        assertThat(entertainment(result)).isEmpty();
    }

    @Test
    void evaluate_whenTwoGovernmentEmploymentsPayTheAllowance_isRefused() {
        // When / Then — the Act gives "the assessee" one sum; per employment or in all is not stated (D-105)
        assertThatThrownBy(() -> runEntertainment(2025, FIRST_SCHEDULE_RATES,
                        List.of(EmployerKind.GOVERNMENT, EmployerKind.GOVERNMENT), "300000", "10000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entertainment");
    }
}
