package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.component.ComponentLifecycle;
import com.thiru.wealthlens.taxengine.rules.component.DeprecationPolicy;
import com.thiru.wealthlens.taxengine.rules.component.SalaryBaseInclusion;
import com.thiru.wealthlens.taxengine.rules.component.TaxTreatment;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The salary bases (D-092): basic, plus dearness allowance where the employment's terms provide, and no other
 * allowance, per base rule and per employment. The shipped registry holds no component until {@code W-064}, so
 * the pack here carries fixture components; the amounts are fixtures and each base is added up by hand.
 */
class SalaryBaseNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final ComponentCode BASIC = new ComponentCode("FIXTURE_BASIC");
    private static final ComponentCode DA = new ComponentCode("FIXTURE_DA");
    private static final ComponentCode HRA = new ComponentCode("FIXTURE_HRA");
    private static final List<RuleId> BASES = List.of(
            RuleIds.SAL_SALARY_FOR_HRA,
            RuleIds.SAL_SALARY_FOR_GRATUITY,
            RuleIds.SAL_SALARY_FOR_EMPLOYER_PENSION,
            RuleIds.SAL_SALARY_FOR_EMPLOYEE_PENSION);

    private static RulePack withFixtureComponents(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack shipped = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        Map<ComponentCode, ComponentDefinition> registry = Stream.of(
                        component(shipped, BASIC, inEveryBase(false)),
                        component(shipped, DA, inEveryBase(true)),
                        component(shipped, HRA, Set.of()))
                .collect(Collectors.toMap(ComponentDefinition::code, definition -> definition));
        return new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), shipped.rules(), registry,
                shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());
    }

    private static Set<SalaryBaseInclusion> inEveryBase(boolean onlyWhereTermsProvide) {
        return BASES.stream()
                .map(rule -> new SalaryBaseInclusion(rule, onlyWhereTermsProvide))
                .collect(Collectors.toSet());
    }

    private static ComponentDefinition component(RulePack pack, ComponentCode code, Set<SalaryBaseInclusion> bases) {
        return new ComponentDefinition(code, "Fixture " + code.value(), HeadCode.SALARIES, ComponentKind.SALARY,
                CashNature.CASH, TaxTreatment.TAXABLE, Set.of(RuleIds.SAL_SALARY_INCLUDES), bases, false,
                new ComponentLifecycle(DateRange.fromOnwards(LocalDate.of(2025, 4, 1)), Optional.empty(),
                        new DeprecationPolicy.HistoricalOnly()),
                pack.rule(RuleIds.SAL_SALARY_INCLUDES).headProvenance());
    }

    private static Employment employment(boolean termsIncludeDearnessAllowance, SalaryComponent... components) {
        return new Employment(List.of(components), termsIncludeDearnessAllowance);
    }

    private static SalaryComponent line(ComponentCode code, String rupees) {
        return new SalaryComponent(code, Money.of(rupees));
    }

    /** The partition's output for these employments, built by hand: every fixture is under SALARIES. */
    private static ComponentSet partitioned(List<Employment> employments) {
        List<Line> lines = new ArrayList<>();
        for (int position = 0; position < employments.size(); position++) {
            for (SalaryComponent component : employments.get(position).components()) {
                lines.add(new Line(position, component.code(), component.annualAmount()));
            }
        }
        Map<HeadCode, List<Line>> byHead = new EnumMap<>(HeadCode.class);
        if (!lines.isEmpty()) {
            byHead.put(HeadCode.SALARIES, lines);
        }
        return new ComponentSet(byHead);
    }

    private static NodeResult run(int year, List<Employment> employments) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)), new EnteredFacts(employments, List.of(), List.of(), List.of(), Optional.empty()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(withFixtureComponents(year))
                .regime(NEW_TAX_REGIME)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, partitioned(employments))
                .run(new SalaryBaseNode());
    }

    private static SalaryBases bases(NodeResult result) {
        return (SalaryBases) result.value();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheTermsAreSilentOnDearnessAllowance_countsBasicOnly(int year) {
        // Given
        List<Employment> employments =
                List.of(employment(false, line(BASIC, "600000"), line(DA, "60000"), line(HRA, "240000")));

        // When
        SalaryBases bases = bases(run(year, employments));

        // Then — 6,00,000: DA is out because the terms do not provide for it; HRA is in no base
        BASES.forEach(rule -> assertThat(bases.of(rule, 0)).isEqualTo(Money.of("600000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheTermsProvideForDearnessAllowance_countsBasicAndDearnessAllowance(int year) {
        // Given
        List<Employment> employments =
                List.of(employment(true, line(BASIC, "600000"), line(DA, "60000"), line(HRA, "240000")));

        // When
        SalaryBases bases = bases(run(year, employments));

        // Then — 6,00,000 + 60,000 = 6,60,000; HRA still out, as "all other allowances"
        BASES.forEach(rule -> assertThat(bases.of(rule, 0)).isEqualTo(Money.of("660000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTwoEmploymentsDifferInTheirTerms_keepsEachEmployersBaseApart(int year) {
        // Given
        List<Employment> employments = List.of(
                employment(true, line(BASIC, "600000"), line(DA, "60000")),
                employment(false, line(BASIC, "300000"), line(DA, "30000")));

        // When
        SalaryBases bases = bases(run(year, employments));

        // Then — employer 0: 6,00,000 + 60,000 = 6,60,000; employer 1: 3,00,000, its DA out; never 9,60,000
        assertThat(bases.of(RuleIds.SAL_SALARY_FOR_HRA, 0)).isEqualTo(Money.of("660000"));
        assertThat(bases.of(RuleIds.SAL_SALARY_FOR_HRA, 1)).isEqualTo(Money.of("300000"));
        assertThatThrownBy(() -> bases.of(RuleIds.SAL_SALARY_FOR_HRA, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 were entered");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenRun_explainsEachBaseInAMeasureLineCitingItsRule(int year) {
        // Given
        List<Employment> employments = List.of(employment(true, line(BASIC, "600000"), line(DA, "60000")));

        // When
        NodeResult result = run(year, employments);

        // Then
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactlyElementsOf(BASES);
        assertThat(result.trace()).allSatisfy(line -> {
            assertThat(line.kind()).isEqualTo(StepKind.MEASURE);
            assertThat(line.node()).isEqualTo(NodeIds.SALARY_BASE);
            assertThat(line.out().asMoney()).isEqualTo(Money.of("660000"));
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenNothingIsEntered_hasEveryBaseAndStillExplainsEach(int year) {
        // When
        NodeResult result = run(year, List.of());

        // Then — P5: four bases with no employment under them, each with its line
        assertThat(bases(result).byRule()).containsOnlyKeys(BASES);
        assertThat(result.value().asMoney()).isEqualTo(Money.ZERO);
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactlyElementsOf(BASES);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenRun_citesTheVersionInForceForTheYear(int year) {
        // When
        TraceLine hra = run(year, List.of()).trace().getFirst();

        // Then — 2025-26 rests on Rules 1962 r.2A as read in the DoR compendium; 2026-27 on Rules 2026 r.279(2)(b)
        assertThat(hra.provenance().sourceUrl().toString())
                .isEqualTo(year == 2025
                        ? "https://dor.gov.in/files/rules_files/IT%20Rules_1.pdf"
                        : "https://egazette.gov.in/WriteReadData/2026/271092.pdf");
    }
}
