package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
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
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The allowance exemptions in {@link ExemptionNode} (Sch. III Table Sl. 12–13 with Rules 2026 r.280; s.10(14) with
 * Rules 1962 r.2BB; D-101). Each figure is worked by hand from the prescribed amounts: ₹3,000 and ₹9,000 per month
 * per child for TY 2026-27 (r.280(2) Sl. 7, 8), ₹100 and ₹300 for TY 2025-26 (r.2BB(2) Sl. 5, 6).
 */
class AllowanceExemptionTest {

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static NodeResult run(int year, RegimeCode regime, ComponentCode allowance, String received,
            AllowanceUse use) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true)), List.of(), List.of(), List.of(use)),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of(HeadCode.SALARIES,
                        List.of(new Line(0, allowance, Money.of(received))))))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_HRA, List.of(Money.ZERO))))
                .run(new ExemptionNode());
    }

    @Test
    void evaluate_whenLessWasSpentOnUniformThanPaid_exemptsWhatWasSpent() {
        // When — 12,000 paid, 8,000 spent
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.UNIFORM_ALLOWANCE, "12000",
                AllowanceUse.spent(ComponentCodes.UNIFORM_ALLOWANCE, Money.of("8000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("8000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_ALW_UNIFORM);
            assertThat(line.kind()).isEqualTo(StepKind.EXEMPTION);
            assertThat(line.limitedBy()).contains("amount actually incurred");
        });
    }

    @Test
    void evaluate_whenConveyanceInTheNewRegime2026_isExemptAndVisiblyUnverified() {
        // When — 20,000 paid, 15,000 spent; r.280(3)'s list read into the new regime (D-101)
        NodeResult result = run(2026, NEW_TAX_REGIME, ComponentCodes.CONVEYANCE_ALLOWANCE, "20000",
                AllowanceUse.spent(ComponentCodes.CONVEYANCE_ALLOWANCE, Money.of("15000")));

        // Then — exempt, and the line says its version is unverified, so the computation is PROVISIONAL
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel()).isEqualTo(VerificationLevel.UNVERIFIED);
    }

    @Test
    void evaluate_whenConveyanceInTheNewRegime2025_isExemptOnClearWords() {
        // When — r.2BB(3) (G.S.R. 452(E)) names (1)(c) for an assessee taxed under s.115BAC(1A)
        NodeResult result = run(2025, NEW_TAX_REGIME, ComponentCodes.CONVEYANCE_ALLOWANCE, "20000",
                AllowanceUse.spent(ComponentCodes.CONVEYANCE_ALLOWANCE, Money.of("15000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel())
                .isEqualTo(VerificationLevel.SOURCE_CHECKED);
    }

    @Test
    void evaluate_whenUniformInTheNewRegime_exemptsNothingAndSaysNothing() {
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, ComponentCodes.UNIFORM_ALLOWANCE, "12000",
                AllowanceUse.spent(ComponentCodes.UNIFORM_ALLOWANCE, Money.of("8000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void evaluate_whenChildrenEducationFor2ChildrenAllYear_exemptsThePrescribedLimit() {
        // When — 3,000 × 2 × 12 = 72,000, against 90,000 paid
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, "90000",
                AllowanceUse.forChildren(ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, 2, 12));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("72000")));
        assertThat(result.trace().getFirst().limitedBy()).contains("prescribed limit");
    }

    @Test
    void evaluate_whenChildrenEducationForThreeChildren_countsTwo() {
        // When — the third child earns nothing: still 3,000 × 2 × 12 = 72,000
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, "120000",
                AllowanceUse.forChildren(ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, 3, 12));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("72000")));
    }

    @Test
    void evaluate_whenHostelForOneChildSixMonths_exemptsSixMonthsOfTheLimit() {
        // When — 9,000 × 1 × 6 = 54,000, against 60,000 paid
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.HOSTEL_ALLOWANCE, "60000",
                AllowanceUse.forChildren(ComponentCodes.HOSTEL_ALLOWANCE, 1, 6));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("54000")));
    }

    @Test
    void evaluate_whenChildrenEducationIn2025_usesTheRule2bbAmount() {
        // When — 100 × 2 × 12 = 2,400, against 30,000 paid
        NodeResult result = run(2025, FIRST_SCHEDULE_RATES, ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, "30000",
                AllowanceUse.forChildren(ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, 2, 12));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("2400")));
    }

    @Test
    void evaluate_whenAPrescribedAllowanceIsEnteredWithOnlyAnAmount_isRefused() {
        // When / Then — Sl. 13 is per child per month; an amount spent says nothing about either
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE,
                        "90000", AllowanceUse.spent(ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, Money.of("50000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("children");
    }

    @Test
    void evaluate_whenAUseNamesAComponentWithNoAllowanceExemption_isRefused() {
        // When / Then — basic pay has no allowance exemption to claim
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.BASIC, "600000",
                        AllowanceUse.spent(ComponentCodes.BASIC, Money.of("1000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BASIC");
    }
}
