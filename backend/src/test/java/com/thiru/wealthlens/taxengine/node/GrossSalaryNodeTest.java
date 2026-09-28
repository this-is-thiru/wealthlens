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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Gross salary: the total of the SALARIES head, as {@code SAL-SALARY-INCLUDES} defines it (S2 s.16; 1961
 * s.17(1); D-091). The lines are fixtures, not law; the sums are worked by hand in each case.
 */
class GrossSalaryNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final ComponentCode BASIC = new ComponentCode("FIXTURE_BASIC");
    private static final ComponentCode HRA = new ComponentCode("FIXTURE_HRA");
    private static final ComponentCode INTEREST = new ComponentCode("FIXTURE_INTEREST");

    /** A line under SALARIES that is not salary — a tax the employee pays — feeding another rule. */
    private static final ComponentCode TAX_PAID = new ComponentCode("FIXTURE_TAX_PAID");

    /**
     * The shipped pack with a fixture registry: pay lines feed the definition of salary; the tax paid sits under
     * the same head but feeds a different rule, as professional tax does (D-093).
     */
    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack shipped = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        Map<ComponentCode, ComponentDefinition> registry = Stream.of(
                        component(shipped, BASIC, HeadCode.SALARIES, RuleIds.SAL_SALARY_INCLUDES),
                        component(shipped, HRA, HeadCode.SALARIES, RuleIds.SAL_SALARY_INCLUDES),
                        component(shipped, TAX_PAID, HeadCode.SALARIES, RuleIds.RATE_SLAB_NEW_TAX_REGIME),
                        component(shipped, INTEREST, HeadCode.OTHER_SOURCES, RuleIds.RATE_SLAB_NEW_TAX_REGIME))
                .collect(Collectors.toMap(ComponentDefinition::code, definition -> definition));
        return new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), shipped.rules(), registry,
                shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());
    }

    private static ComponentDefinition component(RulePack pack, ComponentCode code, HeadCode head, RuleId feeds) {
        return new ComponentDefinition(code, "Fixture " + code.value(), head, ComponentKind.SALARY, CashNature.CASH,
                TaxTreatment.TAXABLE, Set.of(feeds), Set.of(), false,
                new ComponentLifecycle(DateRange.fromOnwards(LocalDate.of(2025, 4, 1)), Optional.empty(),
                        new DeprecationPolicy.HistoricalOnly()),
                pack.rule(RuleIds.SAL_SALARY_INCLUDES).headProvenance());
    }

    private static EngineInputs inYear(int year) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        TaxYearId taxYear = TaxYearId.of(year);
        return new EngineInputs(taxYear, NEW_TAX_REGIME, TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                plain.entered(), plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
    }

    private static NodeResult run(int year, ComponentSet components) {
        return run(year, components, Money.ZERO);
    }

    private static NodeResult run(int year, ComponentSet components, Money perquisites) {
        return run(year, components, perquisites, Money.ZERO);
    }

    private static NodeResult run(int year, ComponentSet components, Money perquisites, Money contributions) {
        return NodeHarness.with(pack(year))
                .regime(NEW_TAX_REGIME)
                .inputs(inYear(year))
                .given(NodeIds.INPUTS_COMPONENTS, components)
                .given(NodeIds.SALARY_PERQUISITES, MoneyValue.of(perquisites))
                .given(NodeIds.EMPLOYER_CONTRIB_CHARGES, MoneyValue.of(contributions))
                .run(new GrossSalaryNode());
    }

    private static final ComponentSet TWO_EMPLOYMENTS = new ComponentSet(Map.of(
            HeadCode.SALARIES, List.of(
                    new Line(0, BASIC, Money.of("600000")),
                    new Line(0, HRA, Money.of("240000")),
                    new Line(1, BASIC, Money.of("300000"))),
            HeadCode.OTHER_SOURCES, List.of(new Line(0, INTEREST, Money.of("5000")))));

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTwoEmploymentsEnterSalary_totalsTheSalariesHeadOnly(int year) {
        // When
        NodeResult result = run(year, TWO_EMPLOYMENTS);

        // Then — 6,00,000 + 2,40,000 + 3,00,000 = 11,40,000; the ₹5,000 of interest is another head's
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1140000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenATaxPaidSitsUnderSalaries_leavesItOutOfGross(int year) {
        // Given — D-093: professional tax is under SALARIES but feeds its own rule, not the definition of salary
        ComponentSet withTaxPaid = new ComponentSet(Map.of(HeadCode.SALARIES, List.of(
                new Line(0, BASIC, Money.of("600000")),
                new Line(0, TAX_PAID, Money.of("2500")))));

        // When
        NodeResult result = run(year, withTaxPaid);

        // Then — 6,00,000 only; adding the ₹2,500 paid would raise salary by a tax paid out of it
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("600000")));
        assertThat(((ComponentSet) result.trace().getFirst().in()).in(HeadCode.SALARIES))
                .extracting(Line::code)
                .containsExactly(BASIC);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenRun_explainsItselfInOneIncomeLineCitingTheDefinition(int year) {
        // When
        NodeResult result = run(year, TWO_EMPLOYMENTS);

        // Then
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_SALARY_INCLUDES);
            assertThat(line.kind()).isEqualTo(StepKind.INCOME);
            assertThat(line.node()).isEqualTo(NodeIds.SALARY_GROSS);
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("1140000")));
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenNothingIsEntered_isNilAndStillExplained(int year) {
        // When
        NodeResult result = run(year, new ComponentSet(Map.of()));

        // Then — P5: a nil figure carries its line too
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.SAL_SALARY_INCLUDES);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenRun_citesTheVersionInForceForTheYear(int year) {
        // When
        TraceLine line = run(year, TWO_EMPLOYMENTS).trace().getFirst();

        // Then — 2025-26 rests on 1961 s.17(1) as amended by FA 2023; 2026-27 on the 2025 Act's s.16
        assertThat(line.provenance().sourceUrl().toString())
                .isEqualTo(year == 2025
                        ? "https://egazette.gov.in/WriteReadData/2023/244830.pdf"
                        : "https://egazette.gov.in/WriteReadData/2025/265620.pdf");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenPerquisitesAreValued_addsThemOnALineOfTheirOwn(int year) {
        // When — accommodation valued at 96,000 (D-111; S2 s.16(e): salary includes perquisites)
        NodeResult result = run(year, TWO_EMPLOYMENTS, Money.of("96000"));

        // Then
        Money pay = result.trace().getFirst().out().asMoney();
        assertThat(result.value()).isEqualTo(MoneyValue.of(pay.plus(Money.of("96000"))));
        assertThat(result.trace()).hasSize(2);
        assertThat(result.trace().get(1).out()).isEqualTo(MoneyValue.of(Money.of("96000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenEmployerContributionsExceedTheLimit_addsTheExcessOnALineOfItsOwn(int year) {
        // When — pay 11,40,000 across two employments, perquisites 60,000, contributions beyond the limit 74,000
        NodeResult result = run(year, TWO_EMPLOYMENTS, Money.of("60000"), Money.of("74000"));

        // Then — 11,40,000 + 60,000 + 74,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1274000")));
        assertThat(result.trace()).hasSize(3);
    }
}
