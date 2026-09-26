package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
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
import java.util.Map;
import java.util.Set;
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

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
    }

    private static EngineInputs inYear(int year) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        TaxYearId taxYear = TaxYearId.of(year);
        return new EngineInputs(taxYear, NEW_TAX_REGIME, TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                plain.entered(), plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
    }

    private static NodeResult run(int year, ComponentSet components) {
        return NodeHarness.with(pack(year))
                .regime(NEW_TAX_REGIME)
                .inputs(inYear(year))
                .given(NodeIds.INPUTS_COMPONENTS, components)
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
}
