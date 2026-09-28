package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.component.ComponentLifecycle;
import com.thiru.wealthlens.taxengine.rules.component.DeprecationPolicy;
import com.thiru.wealthlens.taxengine.rules.component.TaxTreatment;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
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
 * The partition of entered components into heads by the year's registry (D-090, {@code DR 06} G1.1 step 1):
 * a code the registry does not hold for the year stops the run rather than being summed in ({@code D-CLOSEDSUM},
 * O-15). The shipped registry is empty until the first rule reading a component arrives (D-089), so the pack
 * here carries fixture components; the amounts are fixtures too, and no figure is tax.
 */
class ComponentPartitionNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final ComponentCode BASIC = new ComponentCode("FIXTURE_BASIC");
    private static final ComponentCode HRA = new ComponentCode("FIXTURE_HRA");
    private static final ComponentCode INTEREST = new ComponentCode("FIXTURE_INTEREST");
    /** Live only in TY 2025-26. */
    private static final ComponentCode WITHDRAWN = new ComponentCode("FIXTURE_WITHDRAWN");

    private static RulePack withFixtureComponents(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack shipped = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        Map<ComponentCode, ComponentDefinition> registry = Stream.of(
                        component(shipped, BASIC, HeadCode.SALARIES, DateRange.fromOnwards(LocalDate.of(2025, 4, 1))),
                        component(shipped, HRA, HeadCode.SALARIES, DateRange.fromOnwards(LocalDate.of(2025, 4, 1))),
                        component(shipped, INTEREST, HeadCode.OTHER_SOURCES,
                                DateRange.fromOnwards(LocalDate.of(2025, 4, 1))),
                        component(shipped, WITHDRAWN, HeadCode.SALARIES,
                                new DateRange(LocalDate.of(2025, 4, 1), Optional.of(LocalDate.of(2026, 3, 31)))))
                .collect(Collectors.toMap(ComponentDefinition::code, definition -> definition));
        return new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), shipped.rules(), registry,
                shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());
    }

    private static ComponentDefinition component(RulePack pack, ComponentCode code, HeadCode head, DateRange live) {
        return new ComponentDefinition(code, "Fixture " + code.value(), head, ComponentKind.SALARY, CashNature.CASH,
                TaxTreatment.TAXABLE, Set.of(RuleIds.RATE_SLAB_NEW_TAX_REGIME), Set.of(), false,
                new ComponentLifecycle(live, Optional.empty(), new DeprecationPolicy.HistoricalOnly()),
                pack.rule(RuleIds.RATE_SLAB_NEW_TAX_REGIME).headProvenance());
    }

    private static EngineInputs entering(int year, List<Employment> employments) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        TaxYearId taxYear = TaxYearId.of(year);
        return new EngineInputs(taxYear, NEW_TAX_REGIME, TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(employments, List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
    }

    private static Employment employment(SalaryComponent... components) {
        return new Employment(List.of(components), false, EmployerKind.OTHER, false, Optional.empty());
    }

    private static SalaryComponent line(ComponentCode code, String rupees) {
        return new SalaryComponent(code, Money.of(rupees));
    }

    private static NodeResult run(int year, List<Employment> employments) {
        return NodeHarness.with(withFixtureComponents(year))
                .regime(NEW_TAX_REGIME)
                .inputs(entering(year, employments))
                .run(new ComponentPartitionNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTwoEmploymentsEnterComponents_groupsEachLineUnderItsHeadUnsummed(int year) {
        // Given — BASIC in both employments stays two lines; interest belongs to another head
        List<Employment> employments = List.of(
                employment(line(BASIC, "600000"), line(HRA, "240000"), line(INTEREST, "5000")),
                employment(line(BASIC, "300000")));

        // When
        ComponentSet partitioned = (ComponentSet) run(year, employments).value();

        // Then
        assertThat(partitioned.in(HeadCode.SALARIES)).containsExactly(
                new Line(0, BASIC, Money.of("600000")),
                new Line(0, HRA, Money.of("240000")),
                new Line(1, BASIC, Money.of("300000")));
        assertThat(partitioned.in(HeadCode.OTHER_SOURCES)).containsExactly(new Line(0, INTEREST, Money.of("5000")));
        assertThat(partitioned.in(HeadCode.HOUSE_PROPERTY)).isEmpty();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenACodeIsNotInTheRegistry_isRefusedNamingTheCodeTheEmploymentAndTheYear(int year) {
        // Given — well-formed but undefined: O-15's EMPLOR_PF
        List<Employment> employments = List.of(employment(line(BASIC, "600000")),
                employment(line(new ComponentCode("EMPLOR_PF"), "21600")));

        // When / Then
        assertThatThrownBy(() -> run(year, employments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMPLOR_PF")
                .hasMessageContaining("employment 1")
                .hasMessageContaining(TaxYearId.of(year).label());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2026})
    void evaluate_whenACodeIsLiveOnlyInAnotherYear_isRefused(int year) {
        // When / Then — the fixture is live for TY 2025-26 alone
        assertThatThrownBy(() -> run(year, List.of(employment(line(WITHDRAWN, "1000")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(WITHDRAWN.value());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenNothingIsEntered_givesAnEmptySetAndNoLine(int year) {
        // When
        NodeResult result = run(year, List.of());

        // Then — the partition applies no rule, so it explains nothing of its own
        assertThat(((ComponentSet) result.value()).in(HeadCode.SALARIES)).isEmpty();
        assertThat(result.value().asMoney()).isEqualTo(Money.ZERO);
        assertThat(result.trace()).isEmpty();
    }
}
