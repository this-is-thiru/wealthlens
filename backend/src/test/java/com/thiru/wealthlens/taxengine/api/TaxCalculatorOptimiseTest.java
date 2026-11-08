package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.RetirementFundHistory;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The optimiser through {@link TaxCalculator#optimise} (W-238a). Every bill is worked by hand from TY 2026-27's pack rows:
 * new regime {@code RATE-SLAB-NEW-TAX-REGIME} (0 to 4L nil, then 5, 10, 15, 20, 25 % per 4L, 30 % above 24L) after
 * {@code SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME} 75,000, with {@code REBATE-NEW-TAX-REGIME} up to 60,000 to 12L; the
 * old regime {@code FIRST_SCHEDULE_RATES}, {@code RATE-SLAB-FIRST-SCHEDULE} (2.5L nil, 5 % to 5L, 20 % to 10L, 30 % above)
 * after {@code SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE} 50,000; {@code RATE-HEALTH-EDUCATION-CESS} 4 % on both. HRA is
 * {@code SAL-HOUSE-RENT-ALLOWANCE}: the least of the allowance, rent less 10 % of salary, and 50 % of salary in Mumbai,
 * salary being basic pay here; nil in the new regime. Uniform is {@code SAL-ALW-UNIFORM}, the least of the allowance and
 * the amount spent; nil in the new regime ({@code DIS-NEW-TAX-REGIME-NO-EXEMPTION}).
 */
class TaxCalculatorOptimiseTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TY_2026_27)));
    private static final ComponentCode HRA = ComponentCodes.HRA;
    private static final ComponentCode UNIFORM = ComponentCodes.UNIFORM_ALLOWANCE;
    private static final ComponentCode SPECIAL = ComponentCodes.SPECIAL_ALLOWANCE;
    private static final String NEW = "NEW_TAX_REGIME";
    private static final String OLD = "FIRST_SCHEDULE_RATES";
    /** HRA's pack row ({@code components/HRA.json}): SALARY, CASH, feeding the two rules named, sorted. */
    private static final Optimisation.ComponentFacts HRA_FACTS = new Optimisation.ComponentFacts("SALARY", "CASH",
            List.of("SAL-HOUSE-RENT-ALLOWANCE", "SAL-SALARY-INCLUDES"));
    /** The uniform allowance's pack row ({@code components/UNIFORM_ALLOWANCE.json}). */
    private static final Optimisation.ComponentFacts UNIFORM_FACTS = new Optimisation.ComponentFacts("SALARY", "CASH",
            List.of("SAL-ALW-UNIFORM", "SAL-SALARY-INCLUDES"));

    /** Basic 16L, special 8L, rent 9.6L: HRA saturates at min(9.6L − 1.6L, 8L) = 8L, all the special allowance. */
    private static EngineInputs flipsWithHra() {
        return Fixtures.rentingInMumbai("1600000", "800000", "960000", List.of());
    }

    /** Basic 16L, special 8.2L, rent 9.1L (HRA saturates at 7.5L), uniform spending 60,000. */
    private static EngineInputs flipsOnlyWithBoth() {
        return Fixtures.rentingInMumbai("1600000", "820000", "910000",
                List.of(new AllowanceUse(UNIFORM, Optional.of(Money.of("60000")), Optional.empty(), Optional.empty())));
    }

    private static AllocationSpace space(EngineInputs inputs, Set<ComponentCode> movable, String floor, int breadth) {
        return new AllocationSpace(inputs, movable, Set.of(), SPECIAL, Money.of(floor), breadth);
    }

    @Test
    void optimise_whenHraTurnsTheOldRegimeCheaper_movesTheSpecialAllowanceIntoHraAndFlipsTheRegime() {
        // Given — before: new 24L − 75,000 = 23,25,000 → 20,000 + 40,000 + 60,000 + 80,000 + 25 % × 3,25,000 = 2,81,250,
        // + 4 % = 2,92,500; old 23,50,000 → 12,500 + 1,00,000 + 4,05,000 = 5,17,500, + 4 % = 5,38,200. After HRA 8L:
        // old 15,50,000 → 12,500 + 1,00,000 + 1,65,000 = 2,77,500, + 4 % = 2,88,600; new unchanged, 2,92,500.
        AllocationSpace space = space(flipsWithHra(), Set.of(HRA), "0", 6);

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.regimeBefore()).isEqualTo(NEW);
        assertThat(optimised.billBefore()).isEqualTo(Money.of("292500"));
        assertThat(optimised.regimeAfter()).isEqualTo(OLD);
        assertThat(optimised.billAfter()).isEqualTo(Money.of("288600"));
        assertThat(optimised.changes()).containsExactly(new Optimisation.Change("HRA", HRA_FACTS, Money.ZERO,
                Money.of("800000"), Optional.of(Money.of("3900")), Money.ZERO));
        assertThat(optimised.residualAfter()).isEqualTo(Money.ZERO);
        assertThat(optimised.verified()).containsExactly("HRA");
        assertThat(optimised.discrepancy()).isEmpty();
    }

    @Test
    void optimise_whenTheNewRegimeRebateLeavesNothingToSave_suggestsNoChange() {
        // Given — basic 6L, special 6L, rent 3L: new 12L − 75,000 = 11,25,000 → 20,000 + 32,500 = 52,500, rebated in
        // full (income ≤ 12L, at most 60,000) → nil. Old with HRA at its 2,40,000 saturation: 9,10,000 → 12,500 +
        // 82,000 = 94,500, + 4 % = 98,280 — still more than nil.
        AllocationSpace space = space(Fixtures.rentingInMumbai("600000", "600000", "300000", List.of()), Set.of(HRA),
                "0", 6);

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.regimeAfter()).isEqualTo(NEW);
        assertThat(optimised.billBefore()).isEqualTo(Money.ZERO);
        assertThat(optimised.billAfter()).isEqualTo(Money.ZERO);
        assertThat(optimised.changes()).isEmpty();
        assertThat(optimised.residualAfter()).isEqualTo(Money.of("600000"));
    }

    @Test
    void optimise_whenOnlyTwoMovesTogetherFlipTheRegime_returnsTheExhaustiveAnswerWithTheNotice() {
        // Given — gross 24.2L. New: 23,45,000 → 2,00,000 + 25 % × 3,45,000 = 2,86,250, + 4 % = 2,97,700, whatever moves.
        // Old, HRA 7.5L alone: 16,20,000 → 1,12,500 + 1,86,000 = 2,98,500, + 4 % = 3,10,440 — more than new, so nothing
        // alone saves and greedy stops where it began. HRA 7.5L and uniform 60,000: 15,60,000 → 1,12,500 + 1,68,000 =
        // 2,80,500, + 4 % = 2,91,720. Special allowance left: 8,20,000 − 7,50,000 − 60,000 = 10,000.
        AllocationSpace space = space(flipsOnlyWithBoth(), Set.of(HRA, UNIFORM), "0", 6);

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.billBefore()).isEqualTo(Money.of("297700"));
        assertThat(optimised.regimeAfter()).isEqualTo(OLD);
        assertThat(optimised.billAfter()).isEqualTo(Money.of("291720"));
        assertThat(optimised.changes()).containsExactly(
                new Optimisation.Change("HRA", HRA_FACTS, Money.ZERO, Money.of("750000"), Optional.of(Money.ZERO),
                        Money.ZERO),
                new Optimisation.Change("UNIFORM_ALLOWANCE", UNIFORM_FACTS, Money.ZERO, Money.of("60000"),
                        Optional.of(Money.ZERO), Money.ZERO));
        assertThat(optimised.residualAfter()).isEqualTo(Money.of("10000"));
        assertThat(optimised.verified()).containsExactly("HRA", "UNIFORM_ALLOWANCE");
        assertThat(optimised.discrepancy()).hasValueSatisfying(notice -> assertThat(notice)
                .contains("297700").contains("291720").contains("G6.3"));
    }

    @Test
    void optimise_whenTheBreadthCoversOneComponent_missesThePairAndRecordsWhatItVerified() {
        // Given — the case above, verification narrowed to one component (O8's knob): the first round saves nothing
        // anywhere, so the tie goes to code order and HRA is verified alone, uniform held at its greedy nil
        AllocationSpace space = space(flipsOnlyWithBoth(), Set.of(HRA, UNIFORM), "0", 1);

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.billAfter()).isEqualTo(Money.of("297700"));
        assertThat(optimised.changes()).isEmpty();
        assertThat(optimised.verified()).containsExactly("HRA");
        assertThat(optimised.discrepancy()).isEmpty();
    }

    @Test
    void optimise_whenTheFloorEqualsTheTakeHomeAsEntered_stillOptimises() {
        // Given — cash 16L + 8L, less the bill as entered 2,92,500 = 21,07,500
        AllocationSpace space = space(flipsWithHra(), Set.of(HRA), "2107500", 6);

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then — moving pay between cash components cannot lower cash; the bill falls, so take-home rises to 21,11,400
        assertThat(optimised.billAfter()).isEqualTo(Money.of("288600"));
    }

    @Test
    void optimise_whenTheFloorIsAboveTheTakeHomeAsEntered_isRefused() {
        // Given — one rupee above 21,07,500
        AllocationSpace space = space(flipsWithHra(), Set.of(HRA), "2107501", 6);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2107500").hasMessageContaining("2107501");
    }

    @Test
    void optimise_whenTheResidualIsNotInAnyEmployment_isRefused() {
        // Given
        AllocationSpace space = new AllocationSpace(flipsWithHra(), Set.of(HRA), Set.of(), ComponentCodes.CONVEYANCE_ALLOWANCE,
                Money.ZERO, 6);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONVEYANCE_ALLOWANCE").hasMessageContaining("exactly one employment, not 0");
    }

    @Test
    void optimise_whenAMovableCodeIsNotInThePack_isRefused() {
        // Given
        AllocationSpace space = space(flipsWithHra(), Set.of(new ComponentCode("MEAL_COUPON")), "0", 6);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defines no component MEAL_COUPON");
    }

    /**
     * The funds' history with no income accrued in the year: the accretion beyond the limit, (PC ÷ 2 + PC1 + TP1) × I ÷
     * F(avg) ({@code PERQ-EMPLOYER-ACCRETION}, S2 s.17(1)(i)), is then nil, so only the excess itself is a perquisite.
     */
    private static final Optional<RetirementFundHistory> NOTHING_ACCRUED = Optional.of(new RetirementFundHistory(
            Money.ZERO, Money.ZERO, Money.ZERO, Money.of("100000"), Money.of("100000")));

    /** Basic 16L and special 8L from an OTHER employer, with these fund contributions entered (W-238c). */
    private static EngineInputs salaried(List<EmployerFundContribution> contributions) {
        return Fixtures.salaried("1600000", "800000", contributions, NOTHING_ACCRUED);
    }

    private static AllocationSpace funds(EngineInputs inputs, Set<EmployerFund> movable) {
        return new AllocationSpace(inputs, Set.of(), movable, SPECIAL, Money.ZERO, 6);
    }

    @Test
    void optimise_whenTheEmployersNpsIsMovable_fillsItToTheNewRegimeCap() {
        // Given — before: new 24L − 75,000 = 23,25,000 → 2,81,250, + 4 % = 2,92,500. The NPS contribution is salary
        // (SAL-INCLUDES-PENSION-CONTRIBUTION) and deducted to 14 % of basic in the new regime
        // (DED-NPS-EMPLOYER-NEW-TAX-REGIME): 2,24,000. After: 21,01,000 → 2,00,000 + 25 % × 1,01,000 = 2,25,250, + 4 % =
        // 2,34,260. Old after, 10 % (DED-NPS-EMPLOYER-OTHER): 24L − 50,000 − 1,60,000 = 21,90,000 → 1,12,500 + 3,57,000
        // = 4,69,500, + 4 % = 4,88,280 — still more.
        AllocationSpace space = funds(salaried(List.of()), Set.of(EmployerFund.NOTIFIED_PENSION_SCHEME));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.regimeBefore()).isEqualTo(NEW);
        assertThat(optimised.billBefore()).isEqualTo(Money.of("292500"));
        assertThat(optimised.regimeAfter()).isEqualTo(NEW);
        assertThat(optimised.billAfter()).isEqualTo(Money.of("234260"));
        assertThat(optimised.changes()).isEmpty();
        assertThat(optimised.fundChanges()).containsExactly(new Optimisation.FundChange("NOTIFIED_PENSION_SCHEME",
                Money.ZERO, Money.of("224000"), Optional.of(Money.of("58240")), Money.of("-224000")));
        assertThat(optimised.residualAfter()).isEqualTo(Money.of("576000"));
        assertThat(optimised.verified()).containsExactly("NOTIFIED_PENSION_SCHEME");
    }

    @Test
    void optimise_whenTheAggregateLimitBinds_stopsTheNpsAtTheHeadroom() {
        // Given — 6,00,000 to the provident fund leaves 1,50,000 under PERQ-EMPLOYER-CONTRIBUTION's 7,50,000; above it
        // each rupee deducted is added back as a perquisite. After: 23,25,000 − 1,50,000 = 21,75,000 → 2,00,000 +
        // 43,750 = 2,43,750, + 4 % = 2,53,500. The fund is not salary and its 12 % charge is TY 2025-26 only.
        AllocationSpace space = funds(salaried(List.of(new EmployerFundContribution(0,
                EmployerFund.RECOGNISED_PROVIDENT_FUND, Money.of("600000")))), Set.of(EmployerFund.NOTIFIED_PENSION_SCHEME));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.billBefore()).isEqualTo(Money.of("292500"));
        assertThat(optimised.billAfter()).isEqualTo(Money.of("253500"));
        assertThat(optimised.fundChanges()).containsExactly(new Optimisation.FundChange("NOTIFIED_PENSION_SCHEME",
                Money.ZERO, Money.of("150000"), Optional.of(Money.of("39000")), Money.of("-150000")));
    }

    @Test
    void optimise_whenSuperannuationIsMovable_fillsItToTheAggregateLimit() {
        // Given — a superannuation contribution is neither salary nor deducted; only its excess over 7,50,000 is a
        // perquisite. Salary falls rupee for rupee to 7,50,000. After: 16,50,000 − 75,000 = 15,75,000 → 20,000 + 40,000 +
        // 15 % × 3,75,000 = 1,16,250, + 4 % = 1,20,900. Old after: 15,50,000 → 2,77,500, + 4 % = 2,88,600 — more.
        AllocationSpace space = funds(salaried(List.of()), Set.of(EmployerFund.APPROVED_SUPERANNUATION_FUND));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.regimeAfter()).isEqualTo(NEW);
        assertThat(optimised.billAfter()).isEqualTo(Money.of("120900"));
        assertThat(optimised.fundChanges()).containsExactly(new Optimisation.FundChange("APPROVED_SUPERANNUATION_FUND",
                Money.ZERO, Money.of("750000"), Optional.of(Money.of("171600")), Money.of("-750000")));
        assertThat(optimised.residualAfter()).isEqualTo(Money.of("50000"));
    }

    @Test
    void optimise_whenAMoveCrossesTheLimitWithNoFundHistory_isRefusedByTheEngine() {
        // Given — the search tries superannuation above 7,50,000, where the accretion needs the funds' figures
        AllocationSpace space = funds(Fixtures.salaried("1600000", "800000", List.of(), Optional.empty()),
                Set.of(EmployerFund.APPROVED_SUPERANNUATION_FUND));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enter the retirement fund history");
    }

    @Test
    void optimise_whenTheFundIsEnteredTwiceForTheEmployment_isRefused() {
        // Given
        EmployerFundContribution once = new EmployerFundContribution(0, EmployerFund.NOTIFIED_PENSION_SCHEME,
                Money.of("10000"));
        AllocationSpace space = funds(salaried(List.of(once, once)), Set.of(EmployerFund.NOTIFIED_PENSION_SCHEME));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOTIFIED_PENSION_SCHEME is entered more than once");
    }
}
