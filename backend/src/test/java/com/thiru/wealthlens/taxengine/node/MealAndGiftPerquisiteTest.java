package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerGift;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.EmployerMeals;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.MealSetting;
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
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Meals and gifts as perquisites (S4 r.15(5) Table IV Sl. 3–4; Rules 1962 r.3(7)(iii)–(iv); D-117). Each figure is
 * worked by hand from the limits: TY 2026-27 ₹200 per meal and ₹15,000; TY 2025-26 fifty rupees per meal and five
 * thousand rupees, and no voucher limit in the new regime (G.S.R. 452(E)).
 */
class MealAndGiftPerquisiteTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static EmployerMeals meals(MealSetting setting, int count, String cost) {
        return new EmployerMeals(0, setting, count, Money.of(cost), Money.ZERO);
    }

    private static NodeResult run(int year, RegimeCode regime, List<EmployerMeals> meals, List<EmployerGift> gifts) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        List<Employment> employments = Collections.nCopies(2,
                new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty()));
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(employments, List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), meals, gifts, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_ACCOM, List.of(Money.ZERO))))
                .given(NodeIds.SALARY_EXEMPTIONS, MoneyValue.of(Money.ZERO))
                .given(NodeIds.SALARY_MONETARYINCOME, MoneyValue.of(Money.ZERO))
                .run(new PerquisiteNode());
    }

    private static NodeResult meals(int year, RegimeCode regime, EmployerMeals entry) {
        return run(year, regime, List.of(entry), List.of());
    }

    private static NodeResult gifts(int year, EmployerGift... gifts) {
        return run(year, NEW_TAX_REGIME, List.of(), List.of(gifts));
    }

    @Test
    void evaluate_whenMealsAtWorkCostMoreThanTheLimit_onlyTheExcessPerMealIsSalary() {
        // When — (250 − 200) × 220
        NodeResult result = meals(2026, NEW_TAX_REGIME, meals(MealSetting.AT_WORK, 220, "250"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("11000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel())
                .isEqualTo(VerificationLevel.SOURCE_CHECKED);
    }

    @Test
    void evaluate_whenMealsAtWorkAreWithinTheLimit_areNil() {
        // When — ₹180 is under ₹200
        NodeResult result = meals(2026, NEW_TAX_REGIME, meals(MealSetting.AT_WORK, 220, "180"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenTheEmployeePaysPartOfEachMeal_valuesTheMealNetOfIt() {
        // When — 300 − 50 paid = 250; (250 − 200) × 10
        NodeResult result = meals(2026, NEW_TAX_REGIME,
                new EmployerMeals(0, MealSetting.AT_WORK, 10, Money.of("300"), Money.of("50")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("500")));
    }

    @Test
    void evaluate_whenVouchersInTaxYear2026sNewRegime_keepTheLimit() {
        // When — no bar in TY 2026-27: (250 − 200) × 220
        NodeResult result = meals(2026, NEW_TAX_REGIME, meals(MealSetting.PAID_VOUCHER, 220, "250"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("11000")));
    }

    @Test
    void evaluate_whenMealsAtWorkInTaxYear2025_useTheFiftyRupeeLimit() {
        // When — (80 − 50) × 220
        NodeResult result = meals(2025, NEW_TAX_REGIME, meals(MealSetting.AT_WORK, 220, "80"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("6600")));
        assertThat(result.trace().getFirst().provenance().verificationLevel())
                .isEqualTo(VerificationLevel.SOURCE_CHECKED);
    }

    @Test
    void evaluate_whenVouchersInTaxYear2025sNewRegime_areSalaryInFull() {
        // When — G.S.R. 452(E): the voucher limit does not apply in the new regime; 80 × 220
        NodeResult result = meals(2025, NEW_TAX_REGIME, meals(MealSetting.PAID_VOUCHER, 220, "80"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("17600")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_MEAL);
    }

    @Test
    void evaluate_whenVouchersInTaxYear2025OutsideTheNewRegime_keepTheLimit() {
        // When — (80 − 50) × 220
        NodeResult result = meals(2025, FIRST_SCHEDULE_RATES, meals(MealSetting.PAID_VOUCHER, 220, "80"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("6600")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_MEAL_VOUCHER);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenFoodIsOutsideTheExclusions_isSalaryInFull(int year) {
        // When — 12 dinners at ₹1,500
        NodeResult result = meals(year, NEW_TAX_REGIME, meals(MealSetting.OTHERWISE, 12, "1500"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("18000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTeaOrSnacksOrRemoteFood_isNil(int year) {
        // When
        NodeResult tea = meals(year, NEW_TAX_REGIME, meals(MealSetting.TEA_OR_SNACKS_AT_WORK, 250, "40"));
        NodeResult remote = meals(year, NEW_TAX_REGIME, meals(MealSetting.AT_WORK_REMOTE_OR_OFFSHORE, 250, "400"));

        // Then
        assertThat(tea.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(remote.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenGiftsTotalJustBelowTheLimit_areNil() {
        // When
        NodeResult result = gifts(2026, new EmployerGift(0, Money.of("14999")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenGiftsReachTheLimitExactly_areSalaryInFull() {
        // When — "below ₹ 15,000": at 15,000 the whole, not the excess (PERQ-GIFT-BOUNDARY-001)
        NodeResult result = gifts(2026, new EmployerGift(0, Money.of("8000")), new EmployerGift(0, Money.of("7000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("15000")));
    }

    @Test
    void evaluate_whenTwoEmployersEachGiveBelowTheLimit_eachIsNil() {
        // When — 8,000 from each: aggregated per employer (D-117 C4a)
        NodeResult result = gifts(2026, new EmployerGift(0, Money.of("8000")), new EmployerGift(1, Money.of("8000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).hasSize(2);
    }

    @Test
    void evaluate_whenGiftsInTaxYear2025_useTheFiveThousandRupeeLimit() {
        // When
        NodeResult below = gifts(2025, new EmployerGift(0, Money.of("4999")));
        NodeResult at = gifts(2025, new EmployerGift(0, Money.of("5000")));

        // Then
        assertThat(below.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(at.value()).isEqualTo(MoneyValue.of(Money.of("5000")));
    }

    @Test
    void construct_whenTheEmployeePaysMoreThanTheMealCosts_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new EmployerMeals(0, MealSetting.AT_WORK, 1, Money.of("100"), Money.of("150")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between nil and the cost");
    }
}
