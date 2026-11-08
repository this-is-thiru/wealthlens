package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.RetirementFundHistory;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.util.ArrayList;
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
    /** Pay in hand: its row states no withdrawal term and no unstated period (W-238e1). */
    private static final Optimisation.Liquidity NO_LIQUIDITY = new Optimisation.Liquidity(List.of(), List.of());
    /** HRA's pack row ({@code components/HRA.json}): SALARY, CASH, feeding the two rules named, sorted. */
    private static final Optimisation.ComponentFacts HRA_FACTS = new Optimisation.ComponentFacts("SALARY", "CASH",
            List.of("SAL-HOUSE-RENT-ALLOWANCE", "SAL-SALARY-INCLUDES"), NO_LIQUIDITY);
    /** The uniform allowance's pack row ({@code components/UNIFORM_ALLOWANCE.json}). */
    private static final Optimisation.ComponentFacts UNIFORM_FACTS = new Optimisation.ComponentFacts("SALARY", "CASH",
            List.of("SAL-ALW-UNIFORM", "SAL-SALARY-INCLUDES"), NO_LIQUIDITY);

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
        return new AllocationSpace(inputs, new AllocationSpace.Moves(movable, Set.of(), Set.of()), SPECIAL, Money.of(floor), breadth, ProvidentFundTerms.FOLLOW_BASIC);
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
        assertThat(optimised.before().regime()).isEqualTo(NEW);
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("292500"));
        assertThat(optimised.after().regime()).isEqualTo(OLD);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("288600"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("HRA", HRA_FACTS, Money.ZERO,
                Money.of("800000"), Optional.of(Money.of("3900")), Money.ZERO));
        assertThat(optimised.residualAfter()).isEqualTo(Money.ZERO);
        assertThat(optimised.search().verified()).containsExactly("HRA");
        assertThat(optimised.search().discrepancy()).isEmpty();
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
        assertThat(optimised.after().regime()).isEqualTo(NEW);
        assertThat(optimised.before().netPayable()).isEqualTo(Money.ZERO);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.ZERO);
        assertThat(optimised.changes().pay()).isEmpty();
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
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("297700"));
        assertThat(optimised.after().regime()).isEqualTo(OLD);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("291720"));
        assertThat(optimised.changes().pay()).containsExactly(
                new Optimisation.Change("HRA", HRA_FACTS, Money.ZERO, Money.of("750000"), Optional.of(Money.ZERO),
                        Money.ZERO),
                new Optimisation.Change("UNIFORM_ALLOWANCE", UNIFORM_FACTS, Money.ZERO, Money.of("60000"),
                        Optional.of(Money.ZERO), Money.ZERO));
        assertThat(optimised.residualAfter()).isEqualTo(Money.of("10000"));
        assertThat(optimised.search().verified()).containsExactly("HRA", "UNIFORM_ALLOWANCE");
        assertThat(optimised.search().discrepancy()).hasValueSatisfying(notice -> assertThat(notice)
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
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("297700"));
        assertThat(optimised.changes().pay()).isEmpty();
        assertThat(optimised.search().verified()).containsExactly("HRA");
        assertThat(optimised.search().discrepancy()).isEmpty();
    }

    @Test
    void optimise_whenTheFloorEqualsTheTakeHomeAsEntered_stillOptimises() {
        // Given — cash 16L + 8L, less the bill as entered 2,92,500 = 21,07,500
        AllocationSpace space = space(flipsWithHra(), Set.of(HRA), "2107500", 6);

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then — moving pay between cash components cannot lower cash; the bill falls, so take-home rises to 21,11,400
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("288600"));
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
        AllocationSpace space = new AllocationSpace(flipsWithHra(), new AllocationSpace.Moves(Set.of(HRA), Set.of(), Set.of()), ComponentCodes.CONVEYANCE_ALLOWANCE,
                Money.ZERO, 6, ProvidentFundTerms.FOLLOW_BASIC);

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

    /** {@code components/EMPLOYEE_NPS.json}: EMPLOYEE_CONTRIBUTION, paid from take-home, never from the cost to company. */
    @Test
    void optimise_whenAnEmployeeContributionIsMovable_isRefusedNamingItsKind() {
        // Given
        AllocationSpace space = space(flipsWithHra(), Set.of(ComponentCodes.EMPLOYEE_NPS), "0", 6);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMPLOYEE_NPS").hasMessageContaining("EMPLOYEE_CONTRIBUTION")
                .hasMessageContaining("name it under contributions");
    }

    /** {@code components/PROFESSIONAL_TAX.json}: STATUTORY_DEDUCTION. */
    @Test
    void optimise_whenAStatutoryDeductionIsMovable_isRefusedNamingItsKind() {
        // Given
        AllocationSpace space = space(flipsWithHra(), Set.of(HRA, ComponentCodes.PROFESSIONAL_TAX), "0", 6);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROFESSIONAL_TAX").hasMessageContaining("STATUTORY_DEDUCTION");
    }

    /** {@code components/LIFE_INSURANCE_PREMIUM.json}: QUALIFYING_PAYMENT, so it cannot fund a move (W-238g C2a). */
    @Test
    void optimise_whenTheResidualIsNotPay_isRefusedNamingItsKind() {
        // Given
        AllocationSpace space = new AllocationSpace(flipsWithHra(), new AllocationSpace.Moves(Set.of(HRA), Set.of(), Set.of()),
                ComponentCodes.LIFE_INSURANCE_PREMIUM, Money.ZERO, 6, ProvidentFundTerms.FOLLOW_BASIC);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIFE_INSURANCE_PREMIUM").hasMessageContaining("QUALIFYING_PAYMENT");
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

    /**
     * The rules each fund's row lists in TY 2026-27 ({@code exec/funds/}, W-238e): those whose conditions read the fund,
     * and the aggregate limit and its accretion, which take every contribution. Every rule named has a version from
     * 2026-04-01 except {@code PERQ-RPF-CONTRIBUTION}, whose one version ends 2026-03-31, so the year drops it.
     */
    private static final List<String> NPS_FEEDS = List.of("DED-NPS-EMPLOYER-GOVT", "DED-NPS-EMPLOYER-NEW-TAX-REGIME",
            "DED-NPS-EMPLOYER-OTHER", "PERQ-EMPLOYER-ACCRETION", "PERQ-EMPLOYER-CONTRIBUTION",
            "SAL-INCLUDES-PENSION-CONTRIBUTION");
    private static final List<String> AGGREGATE_FEEDS = List.of("PERQ-EMPLOYER-ACCRETION", "PERQ-EMPLOYER-CONTRIBUTION");

    /**
     * The NPS terms in force in TY 2026-27, by first day then event: the 2025 amendment's regulations 3(1)(a), (b),
     * 4(1)(a), (b) and 8(1), each from its Gazette issue of 15-12-2025 (C1a); the 2026 amendment leaves them alone.
     */
    private static final List<String> NPS_2026_27 = List.of(
            "Exit after fifteen years, at sixty or on superannuation, non-government sector from 2025-12-15",
            "Exit at superannuation, government sector from 2025-12-15",
            "Exit on resignation, dismissal or removal, government sector from 2025-12-15",
            "Partial withdrawal from 2025-12-15",
            "Voluntary exit before then, non-government sector from 2025-12-15");
    /** The 1952 Scheme's terms unstated until the 2026 Scheme of 29-06-2026 superseded it (C2a); then paras 49 and 46. */
    private static final List<String> EPF_2026_27 = List.of(
            "Full withdrawal from 2026-06-29",
            "Partial withdrawal from 2026-06-29",
            "unstated from 2025-04-01 to 2026-06-28");

    /** Each term as "event from day [to day]", then each unstated period, in published order. */
    private static List<String> outline(Optimisation.Liquidity liquidity) {
        List<String> lines = new ArrayList<>();
        liquidity.terms().forEach(term -> lines.add(term.event() + " from " + term.from()
                + term.to().map(end -> " to " + end).orElse("")));
        liquidity.unstated().forEach(gap -> lines.add("unstated from " + gap.from()
                + gap.to().map(end -> " to " + end).orElse("")));
        return lines;
    }

    private static AllocationSpace funds(EngineInputs inputs, Set<EmployerFund> movable) {
        return new AllocationSpace(inputs, new AllocationSpace.Moves(Set.of(), movable, Set.of()), SPECIAL, Money.ZERO, 6, ProvidentFundTerms.FOLLOW_BASIC);
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
        assertThat(optimised.before().regime()).isEqualTo(NEW);
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("292500"));
        assertThat(optimised.after().regime()).isEqualTo(NEW);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("234260"));
        assertThat(optimised.changes().pay()).isEmpty();
        assertThat(optimised.changes().funds()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity").containsExactly(
                new Optimisation.FundChange("NOTIFIED_PENSION_SCHEME", NPS_FEEDS, Money.ZERO, Money.of("224000"),
                        Optional.of(Money.of("58240")), Money.of("-224000"), NO_LIQUIDITY));
        assertThat(outline(optimised.changes().funds().getFirst().liquidity())).containsExactlyElementsOf(NPS_2026_27);
        // reg. 4(1)(a) as the 2025 amendment substitutes it: "at least twenty percent" buys an annuity; up to "rupees
        // eight lakh" may all be withdrawn
        assertThat(optimised.changes().funds().getFirst().liquidity().terms().getFirst().terms())
                .contains("at least 20% of the accumulated pension wealth buys an annuity").contains("eight lakh rupees or less may all be withdrawn");
        assertThat(optimised.residualAfter()).isEqualTo(Money.of("576000"));
        assertThat(optimised.search().verified()).containsExactly("NOTIFIED_PENSION_SCHEME");
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
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("292500"));
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("253500"));
        assertThat(optimised.changes().funds()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity").containsExactly(
                new Optimisation.FundChange("NOTIFIED_PENSION_SCHEME", NPS_FEEDS, Money.ZERO, Money.of("150000"),
                        Optional.of(Money.of("39000")), Money.of("-150000"), NO_LIQUIDITY));
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
        assertThat(optimised.after().regime()).isEqualTo(NEW);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("120900"));
        assertThat(optimised.changes().funds()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity").containsExactly(
                new Optimisation.FundChange("APPROVED_SUPERANNUATION_FUND", AGGREGATE_FEEDS, Money.ZERO,
                        Money.of("750000"), Optional.of(Money.of("171600")), Money.of("-750000"), NO_LIQUIDITY));
        assertThat(outline(optimised.changes().funds().getFirst().liquidity()))
                .containsExactly("Payment from the fund from 2026-04-01");
        assertThat(optimised.residualAfter()).isEqualTo(Money.of("50000"));
        assertThat(optimised.search().unpriced()).isEmpty();
    }

    /** What the answer says when an allocation tried needed the funds' history for the accretion (W-238h C2a). */
    private static final Optimisation.UnpricedNotice HISTORY_NEEDED = new Optimisation.UnpricedNotice(
            "retirementFundHistory", "PERQ-EMPLOYER-ACCRETION");

    @Test
    void optimise_whenAMoveCrossesTheLimitWithNoFundHistory_stopsAtTheLimitAndSaysWhy() {
        // Given — the search probes superannuation above 7,50,000, where the accretion needs the funds' figures; below
        // it the case is the one above, worked by hand: 0 → 7,50,000, 2,92,500 → 1,20,900, saving 1,71,600
        AllocationSpace space = funds(Fixtures.salaried("1600000", "800000", List.of(), Optional.empty()),
                Set.of(EmployerFund.APPROVED_SUPERANNUATION_FUND));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("292500"));
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("120900"));
        assertThat(optimised.changes().funds()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity").containsExactly(
                new Optimisation.FundChange("APPROVED_SUPERANNUATION_FUND", AGGREGATE_FEEDS, Money.ZERO,
                        Money.of("750000"), Optional.of(Money.of("171600")), Money.of("-750000"), NO_LIQUIDITY));
        assertThat(optimised.residualAfter()).isEqualTo(Money.of("50000"));
        assertThat(optimised.search().unpriced()).contains(HISTORY_NEEDED);
    }

    @Test
    void optimise_whenTwoFundsMayMoveWithNoFundHistory_answersAtTheLimit() {
        // Given — together the funds cross 7,50,000 where neither does alone. Within the limit superannuation lowers
        // salary rupee for rupee, and the NPS does up to its 2,24,000 deduction; beyond it the excess is a perquisite.
        // So the least bill is salary 7,50,000 lower however it is split: 15,75,000 taxable → 1,20,900
        AllocationSpace space = funds(Fixtures.salaried("1600000", "800000", List.of(), Optional.empty()),
                Set.of(EmployerFund.NOTIFIED_PENSION_SCHEME, EmployerFund.APPROVED_SUPERANNUATION_FUND));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("120900"));
        assertThat(optimised.search().unpriced()).contains(HISTORY_NEEDED);
    }

    @Test
    void optimise_whenThePayAsEnteredIsAboveTheLimitWithNoFundHistory_isRefusedNamingTheHistory() {
        // Given — 8,00,000 entered to the provident fund is already beyond 7,50,000, so nothing can be priced
        AllocationSpace space = funds(Fixtures.salaried("1600000", "800000", List.of(new EmployerFundContribution(0,
                EmployerFund.RECOGNISED_PROVIDENT_FUND, Money.of("800000"))), Optional.empty()),
                Set.of(EmployerFund.NOTIFIED_PENSION_SCHEME));

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

    private static final ComponentCode BASIC = ComponentCodes.BASIC;
    /** Basic pay's pack row ({@code components/BASIC.json}): SALARY, CASH, feeding salary only. */
    private static final Optimisation.ComponentFacts BASIC_FACTS = new Optimisation.ComponentFacts("SALARY", "CASH",
            List.of("SAL-SALARY-INCLUDES"), NO_LIQUIDITY);
    private static final EmployerFundContribution PROVIDENT_FUND_120000 = new EmployerFundContribution(0,
            EmployerFund.RECOGNISED_PROVIDENT_FUND, Money.of("120000"));

    /**
     * Basic as given and special 14L from an OTHER employer, with these pay lines added and contributions entered, and
     * whether dearness allowance counts for retirement (W-238d).
     */
    private static EngineInputs paidBasic(String basic, List<SalaryComponent> added,
            List<EmployerFundContribution> contributions, boolean dearnessAllowanceCountsForRetirement) {
        EngineInputs paid = Fixtures.salaried(basic, "1400000", contributions, NOTHING_ACCRUED);
        Employment held = paid.entered().employments().getFirst();
        List<SalaryComponent> components = new ArrayList<>(held.components());
        components.addAll(added);
        Employment changed = new Employment(components, held.termsIncludeDearnessAllowance(), held.employerKind(),
                dearnessAllowanceCountsForRetirement, held.companyEmployer());
        return new EngineInputs(paid.taxYear(), paid.regime(), paid.temporal(),
                paid.entered().withEmployments(List.of(changed)), paid.assessee(), paid.family(), paid.ledgers(),
                paid.elections(), paid.capitalGains(), paid.filing());
    }

    private static AllocationSpace basicMoves(EngineInputs inputs) {
        return new AllocationSpace(inputs, new AllocationSpace.Moves(Set.of(BASIC), Set.of(), Set.of()), SPECIAL, Money.ZERO, 6, ProvidentFundTerms.FOLLOW_BASIC);
    }

    @Test
    void optimise_whenTheProvidentFundFollowsBasic_movesBasicUntilTheResidualIsSpent() {
        // Given — before: 24L − 75,000 = 23,25,000 → 2,81,250, + 4 % = 2,92,500 (new). The fund follows basic at the
        // entered 1,20,000 ÷ 10,00,000, so each rupee of basic takes 1.12 from special and salary falls 0.12 a rupee:
        // basic rises 14,00,000 ÷ 1.12 = 12,50,000 to 22,50,000, the fund to 2,70,000 (under 7,50,000), special to nil.
        // After: 22,50,000 − 75,000 = 21,75,000 → 2,00,000 + 43,750 = 2,43,750, + 4 % = 2,53,500. Old after: 22,00,000 →
        // 1,12,500 + 3,60,000 = 4,72,500, + 4 % = 4,91,400 — more. Cash: +12,50,000 − 14,00,000 = −1,50,000.
        AllocationSpace space = basicMoves(paidBasic("1000000", List.of(), List.of(PROVIDENT_FUND_120000), false));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("292500"));
        assertThat(optimised.after().regime()).isEqualTo(NEW);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("253500"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("BASIC", BASIC_FACTS,
                Money.of("1000000"), Money.of("2250000"), Optional.of(Money.of("39000")), Money.of("-150000")));
        assertThat(optimised.changes().funds()).isEmpty();
        assertThat(optimised.changes().following()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity").containsExactly(new Optimisation.FollowingChange(
                "RECOGNISED_PROVIDENT_FUND", AGGREGATE_FEEDS, Money.of("120000"), Money.of("270000"), List.of("BASIC"),
                NO_LIQUIDITY));
        assertThat(outline(optimised.changes().following().getFirst().liquidity())).containsExactlyElementsOf(EPF_2026_27);
        assertThat(optimised.residualAfter()).isEqualTo(Money.ZERO);
    }

    @Test
    void optimise_whenTheEmployeesProvidentFundFollowsToo_countsItsWithholdingInTheCashImpact() {
        // Given — as the case above, plus 1,20,000 withheld to the fund. Schedule XV is not in the new regime, so the bill
        // is the same 2,53,500. The withholding rises to 2,70,000: cash −1,50,000 − 1,50,000 = −3,00,000. Old after:
        // 22,00,000 − 1,50,000 (the Schedule XV cap) = 20,50,000 → 1,12,500 + 3,15,000 = 4,27,500, + 4 % = 4,44,600.
        AllocationSpace space = basicMoves(paidBasic("1000000",
                List.of(new SalaryComponent(ComponentCodes.EMPLOYEE_PF, Money.of("120000"))),
                List.of(PROVIDENT_FUND_120000), false));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().regime()).isEqualTo(NEW);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("253500"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("BASIC", BASIC_FACTS,
                Money.of("1000000"), Money.of("2250000"), Optional.of(Money.of("39000")), Money.of("-300000")));
        assertThat(optimised.changes().following()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity").containsExactly(
                new Optimisation.FollowingChange("EMPLOYEE_PF", List.of("DED-SCHEDULE-XV"), Money.of("120000"),
                        Money.of("270000"), List.of("BASIC"), NO_LIQUIDITY),
                new Optimisation.FollowingChange("RECOGNISED_PROVIDENT_FUND", AGGREGATE_FEEDS, Money.of("120000"),
                        Money.of("270000"), List.of("BASIC"), NO_LIQUIDITY));
        assertThat(optimised.changes().following()).allSatisfy(change ->
                assertThat(outline(change.liquidity())).containsExactlyElementsOf(EPF_2026_27));
    }

    @Test
    void optimise_whenNothingFollowsBasic_suggestsNoChange() {
        // Given — basic and special are both salary, so no move between them changes the bill
        AllocationSpace space = basicMoves(paidBasic("1000000", List.of(), List.of(), false));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("292500"));
        assertThat(optimised.changes().pay()).isEmpty();
        assertThat(optimised.changes().following()).isEmpty();
    }

    @Test
    void optimise_whenBasicIsNilAndTheFundFollowsIt_isRefused() {
        // Given
        AllocationSpace space = basicMoves(paidBasic("0", List.of(), List.of(PROVIDENT_FUND_120000), false));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BASIC must be entered above nil");
    }

    @Test
    void optimise_whenDearnessAllowanceCountsForRetirement_isRefused() {
        // Given
        AllocationSpace space = basicMoves(paidBasic("1000000",
                List.of(new SalaryComponent(ComponentCodes.DEARNESS_ALLOWANCE, Money.of("100000"))),
                List.of(PROVIDENT_FUND_120000), true));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dearness allowance counts for retirement");
    }

    @Test
    void optimise_whenTheFollowerIsEnteredTwice_isRefused() {
        // Given
        AllocationSpace space = basicMoves(paidBasic("1000000", List.of(),
                List.of(PROVIDENT_FUND_120000, PROVIDENT_FUND_120000), false));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no one ratio to basic");
    }

    @Test
    void optimise_whenTheEmployeesProvidentFundIsAlsoMovable_isRefusedAsNotPay() {
        // Given
        AllocationSpace space = new AllocationSpace(paidBasic("1000000",
                List.of(new SalaryComponent(ComponentCodes.EMPLOYEE_PF, Money.of("120000"))), List.of(), false), new AllocationSpace.Moves(Set.of(BASIC, ComponentCodes.EMPLOYEE_PF), Set.of(), Set.of()), SPECIAL, Money.ZERO, 6, ProvidentFundTerms.FOLLOW_BASIC);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMPLOYEE_PF").hasMessageContaining("EMPLOYEE_CONTRIBUTION");
    }

    private static AllocationSpace basicMovesUnder(EngineInputs inputs, ProvidentFundTerms terms) {
        return new AllocationSpace(inputs, new AllocationSpace.Moves(Set.of(BASIC), Set.of(), Set.of()), SPECIAL, Money.ZERO, 6, terms);
    }

    @Test
    void optimise_whenTheEmployersShareDoesNotFollowBasic_holdsItAndSuggestsNoChange() {
        // Given — as the first W-238d case, but the employer's 1,20,000 is held (W-238i C2a): basic + special stay
        // 24,00,000 whatever the split, so salary and the bill (2,92,500) do not move. With the share following, the
        // same input saves 39,000.
        AllocationSpace space = basicMovesUnder(paidBasic("1000000", List.of(), List.of(PROVIDENT_FUND_120000), false),
                new ProvidentFundTerms(false, true, Optional.empty()));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("292500"));
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("292500"));
        assertThat(optimised.changes().pay()).isEmpty();
        assertThat(optimised.changes().following()).isEmpty();
    }

    @Test
    void optimise_whenOnlyTheEmployeesShareIsHeld_movesTheEmployersAloneAndLeavesTheWithholdingOutOfTheCashImpact() {
        // Given — the second W-238d case with EMPLOYEE_PF held: the employer's share follows as before (basic 22,50,000,
        // fund 2,70,000, bill 2,53,500, saving 39,000), but the withholding stays 1,20,000, so the cash impact is only
        // +12,50,000 − 14,00,000 = −1,50,000, not −3,00,000.
        AllocationSpace space = basicMovesUnder(paidBasic("1000000",
                List.of(new SalaryComponent(ComponentCodes.EMPLOYEE_PF, Money.of("120000"))),
                List.of(PROVIDENT_FUND_120000), false), new ProvidentFundTerms(true, false, Optional.empty()));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().regime()).isEqualTo(NEW);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("253500"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("BASIC", BASIC_FACTS,
                Money.of("1000000"), Money.of("2250000"), Optional.of(Money.of("39000")), Money.of("-150000")));
        assertThat(optimised.changes().following()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity")
                .containsExactly(new Optimisation.FollowingChange("RECOGNISED_PROVIDENT_FUND", AGGREGATE_FEEDS,
                        Money.of("120000"), Money.of("270000"), List.of("BASIC"), NO_LIQUIDITY));
    }

    @Test
    void optimise_whenDearnessAllowanceCountsForRetirementButNothingFollowsBasic_answers() {
        // Given — basic 10,00,000, DA 1,00,000, special 14,00,000, the employer's share held: salary is 25,00,000 under
        // any split. 25,00,000 − 75,000 = 24,25,000 → 3,00,000 + 30 % × 25,000 = 3,07,500, + 4 % = 3,19,800.
        AllocationSpace space = basicMovesUnder(paidBasic("1000000",
                List.of(new SalaryComponent(ComponentCodes.DEARNESS_ALLOWANCE, Money.of("100000"))),
                List.of(PROVIDENT_FUND_120000), true), new ProvidentFundTerms(false, true, Optional.empty()));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("319800"));
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("319800"));
        assertThat(optimised.changes().pay()).isEmpty();
    }

    @Test
    void optimise_whenAShareIsHeldButNotEntered_isRefused() {
        // Given
        AllocationSpace space = basicMovesUnder(paidBasic("1000000", List.of(), List.of(PROVIDENT_FUND_120000), false),
                new ProvidentFundTerms(true, false, Optional.empty()));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("employeeShareFollowsBasic is false").hasMessageContaining("not entered");
    }

    @Test
    void optimise_whenAShareIsHeldButBasicIsNotMovable_isRefused() {
        // Given
        AllocationSpace space = new AllocationSpace(paidBasic("1000000", List.of(), List.of(PROVIDENT_FUND_120000), false), new AllocationSpace.Moves(Set.of(UNIFORM), Set.of(), Set.of()), SPECIAL, Money.ZERO, 6, new ProvidentFundTerms(false, true, Optional.empty()));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("employerShareFollowsBasic is false").hasMessageContaining("BASIC is not movable");
    }

    private static final SalaryComponent DEARNESS_ALLOWANCE_200000 = new SalaryComponent(
            ComponentCodes.DEARNESS_ALLOWANCE, Money.of("200000"));

    private static ProvidentFundTerms baseIncludesDearnessAllowance(boolean includes) {
        return new ProvidentFundTerms(true, true, Optional.of(includes));
    }

    @Test
    void optimise_whenTheBaseIncludesDearnessAllowance_followsOverBasicAndTheAllowance() {
        // Given — basic 10L, DA 2L counting for retirement, special 14L, the employer's 1,44,000 = 12 % of 12L. Before:
        // 26L − 75,000 = 25,25,000 → 3,00,000 + 37,500 = 3,37,500, + 4 % = 3,51,000. DA is held, so each rupee of basic
        // takes 1 + 0.12 from special: 14,00,000 ÷ 1.12 = 12,50,000, basic 22,50,000, the fund 0.12 × 24,50,000 =
        // 2,94,000. After: 24,50,000 − 75,000 = 23,75,000 → 2,00,000 + 93,750 = 2,93,750, + 4 % = 3,05,500; saving
        // 45,500. Old after: 24,00,000 → 1,12,500 + 4,20,000 = 5,32,500, + 4 % = 5,53,800 — more.
        AllocationSpace space = basicMovesUnder(paidBasic("1000000", List.of(DEARNESS_ALLOWANCE_200000),
                List.of(new EmployerFundContribution(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, Money.of("144000"))),
                true), baseIncludesDearnessAllowance(true));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("351000"));
        assertThat(optimised.after().regime()).isEqualTo(NEW);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("305500"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("BASIC", BASIC_FACTS,
                Money.of("1000000"), Money.of("2250000"), Optional.of(Money.of("45500")), Money.of("-150000")));
        assertThat(optimised.changes().following()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity")
                .containsExactly(new Optimisation.FollowingChange("RECOGNISED_PROVIDENT_FUND", AGGREGATE_FEEDS,
                        Money.of("144000"), Money.of("294000"), List.of("BASIC"), NO_LIQUIDITY));
        assertThat(optimised.residualAfter()).isEqualTo(Money.ZERO);
    }

    @Test
    void optimise_whenTheBaseExcludesDearnessAllowance_followsOverBasicAlone() {
        // Given — as above with the employer's 1,20,000 = 12 % of basic alone: 14,00,000 ÷ 1.12 = 12,50,000, basic
        // 22,50,000, the fund 2,70,000; salary after 24,50,000 as above, so the bill 3,05,500 and the saving 45,500
        AllocationSpace space = basicMovesUnder(paidBasic("1000000", List.of(DEARNESS_ALLOWANCE_200000),
                List.of(PROVIDENT_FUND_120000), true), baseIncludesDearnessAllowance(false));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("305500"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("BASIC", BASIC_FACTS,
                Money.of("1000000"), Money.of("2250000"), Optional.of(Money.of("45500")), Money.of("-150000")));
        assertThat(optimised.changes().following()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity")
                .containsExactly(new Optimisation.FollowingChange("RECOGNISED_PROVIDENT_FUND", AGGREGATE_FEEDS,
                        Money.of("120000"), Money.of("270000"), List.of("BASIC"), NO_LIQUIDITY));
    }

    @Test
    void optimise_whenTheAllowanceMovesWithItsBaseUnstated_isRefused() {
        // Given — counting for retirement, the base not stated: moving with basic, and (W-238j1) alone
        EngineInputs inputs = paidBasic("1000000", List.of(DEARNESS_ALLOWANCE_200000), List.of(PROVIDENT_FUND_120000),
                true);
        AllocationSpace withBasic = new AllocationSpace(inputs, new AllocationSpace.Moves(
                Set.of(BASIC, ComponentCodes.DEARNESS_ALLOWANCE), Set.of(), Set.of()), SPECIAL, Money.ZERO, 6,
                ProvidentFundTerms.FOLLOW_BASIC);
        AllocationSpace alone = new AllocationSpace(inputs, new AllocationSpace.Moves(
                Set.of(ComponentCodes.DEARNESS_ALLOWANCE), Set.of(), Set.of()), SPECIAL, Money.ZERO, 6,
                ProvidentFundTerms.FOLLOW_BASIC);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(withBasic)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEARNESS_ALLOWANCE cannot move here");
        assertThatThrownBy(() -> CALCULATOR.optimise(alone)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEARNESS_ALLOWANCE cannot move here")
                .hasMessageContaining("whether the provident fund follows it too is not entered");
    }

    /** The dearness allowance's pack row ({@code components/DEARNESS_ALLOWANCE.json}): SALARY, CASH, one rule fed. */
    private static final Optimisation.ComponentFacts DEARNESS_ALLOWANCE_FACTS = new Optimisation.ComponentFacts(
            "SALARY", "CASH", List.of("SAL-SALARY-INCLUDES"), NO_LIQUIDITY);

    private static AllocationSpace allowanceMovesUnder(EngineInputs inputs, ProvidentFundTerms terms) {
        return new AllocationSpace(inputs, new AllocationSpace.Moves(Set.of(ComponentCodes.DEARNESS_ALLOWANCE),
                Set.of(), Set.of()), SPECIAL, Money.ZERO, 6, terms);
    }

    @Test
    void optimise_whenTheAllowanceInTheBaseIsMovable_leadsTheFundAsBasicDoes() {
        // Given — W-238j's case with DA moving instead of basic (W-238j1). Before: 26L − 75,000 = 25,25,000 → 3,51,000.
        // Basic is held, so each rupee of DA takes 1 + 0.12 from special: 14,00,000 ÷ 1.12 = 12,50,000, DA 14,50,000,
        // the fund 0.12 × 24,50,000 = 2,94,000. After: 23,75,000 → 2,93,750, + 4 % = 3,05,500; saving 45,500. Cash:
        // +12,50,000 − 14,00,000 = −1,50,000.
        AllocationSpace space = allowanceMovesUnder(paidBasic("1000000", List.of(DEARNESS_ALLOWANCE_200000),
                List.of(new EmployerFundContribution(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, Money.of("144000"))),
                true), baseIncludesDearnessAllowance(true));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("351000"));
        assertThat(optimised.after().regime()).isEqualTo(NEW);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("305500"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("DEARNESS_ALLOWANCE",
                DEARNESS_ALLOWANCE_FACTS, Money.of("200000"), Money.of("1450000"), Optional.of(Money.of("45500")),
                Money.of("-150000")));
        assertThat(optimised.changes().following()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity")
                .containsExactly(new Optimisation.FollowingChange("RECOGNISED_PROVIDENT_FUND", AGGREGATE_FEEDS,
                        Money.of("144000"), Money.of("294000"), List.of("DEARNESS_ALLOWANCE"), NO_LIQUIDITY));
        assertThat(optimised.residualAfter()).isEqualTo(Money.ZERO);
    }

    @Test
    void optimise_whenTheAllowanceIsMovableButNotEnteredAndMayBeInTheBase_isRefused() {
        // Given — no DA entered; it counts for retirement and the base is not stated (W-238j1 C3a)
        AllocationSpace space = allowanceMovesUnder(paidBasic("1000000", List.of(), List.of(PROVIDENT_FUND_120000),
                true), ProvidentFundTerms.FOLLOW_BASIC);

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEARNESS_ALLOWANCE is not entered");
    }

    @Test
    void optimise_whenTheEmployersShareIsHeldWhileTheAllowanceLeads_answersWithNoChange() {
        // Given — DA counts for retirement with the base unstated, so it is in the base and leads; the employer's share is
        // held, no employee share is entered, so nothing follows; DA and special are taxed alike, so no move saves
        AllocationSpace space = allowanceMovesUnder(paidBasic("1000000", List.of(DEARNESS_ALLOWANCE_200000),
                List.of(new EmployerFundContribution(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, Money.of("144000"))),
                true), new ProvidentFundTerms(false, true, Optional.empty()));

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("351000"));
        assertThat(optimised.changes().pay()).isEmpty();
        assertThat(optimised.changes().following()).isEmpty();
    }

    @Test
    void optimise_whenTheBaseIsStatedButChangesNothing_isRefusedSayingWhy() {
        // Given — BASIC not movable; no share following; no dearness allowance entered
        AllocationSpace basicStill = new AllocationSpace(paidBasic("1000000", List.of(DEARNESS_ALLOWANCE_200000),
                List.of(PROVIDENT_FUND_120000), false), new AllocationSpace.Moves(Set.of(UNIFORM), Set.of(), Set.of()), SPECIAL, Money.ZERO, 6,
                baseIncludesDearnessAllowance(true));
        AllocationSpace nothingFollows = basicMovesUnder(paidBasic("1000000", List.of(DEARNESS_ALLOWANCE_200000),
                List.of(), false), baseIncludesDearnessAllowance(true));
        AllocationSpace noAllowance = basicMovesUnder(paidBasic("1000000", List.of(), List.of(PROVIDENT_FUND_120000),
                false), baseIncludesDearnessAllowance(false));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(basicStill)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BASIC is not movable");
        assertThatThrownBy(() -> CALCULATOR.optimise(nothingFollows)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no provident fund share follows basic");
        assertThatThrownBy(() -> CALCULATOR.optimise(noAllowance)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no dearness allowance is entered");
    }

    // W-238k — an employee's own contribution, added on top of what is entered and paid from take-home. Figures: slabs
    // R3 (01-rulebook.md:88-100, :117, :1689), standard deductions :540-541, cess 4 % :458; Schedule XV's 1,50,000
    // ceiling (DED-80C-001, :1076) with the NPS sub-cap at 10 % of salary first (DED-80C-NPS-CAP-001, :1078); own NPS
    // 50,000 on what the sub-cap left (DED-NPS-SELF-001, :972; 08-golden-tests.md T5-1). Both old regime only.

    /** Employee NPS's pack row ({@code components/EMPLOYEE_NPS.json}): the two rules it feeds, sorted. */
    private static final List<String> EMPLOYEE_NPS_FEEDS = List.of("DED-NPS-OWN", "DED-SCHEDULE-XV");

    private static AllocationSpace contributing(EngineInputs inputs, Set<ComponentCode> movable,
            Set<ComponentCode> contributions, String floor) {
        return new AllocationSpace(inputs, new AllocationSpace.Moves(movable, Set.of(), contributions), SPECIAL,
                Money.of(floor), 6, ProvidentFundTerms.FOLLOW_BASIC);
    }

    /** {@link #flipsWithHra} with these pay lines added to its one employment. */
    private static EngineInputs flipsWithHraAnd(SalaryComponent... added) {
        EngineInputs paid = flipsWithHra();
        Employment held = paid.entered().employments().getFirst();
        List<SalaryComponent> components = new ArrayList<>(held.components());
        components.addAll(List.of(added));
        Employment changed = new Employment(components, held.termsIncludeDearnessAllowance(), held.employerKind(),
                held.dearnessAllowanceCountsForRetirement(), held.companyEmployer());
        return new EngineInputs(paid.taxYear(), paid.regime(), paid.temporal(),
                paid.entered().withEmployments(List.of(changed)), paid.assessee(), paid.family(), paid.ledgers(),
                paid.elections(), paid.capitalGains(), paid.filing());
    }

    @Test
    void optimise_whenTheOwnNpsLiesPastTheScheduleXvCeiling_addsTheNpsUntilOwnNpsIsFull() {
        // Given — salary for the sub-cap is basic 16L, so the sub-cap is 1,60,000. NPS's point under the old regime is
        // the least addition reaching total income at the ceiling (24L in hand): 1,60,000 + 50,000 = 2,10,000, where
        // s.123 allows 1,50,000 and own NPS 50,000. Round 1: HRA 8L saves 2,92,500 − 2,88,600 = 3,900; NPS alone saves
        // nil (old 21,50,000 → 1,12,500 + 3,45,000 = 4,57,500, + 4 % = 4,75,800, above the new 2,92,500). Round 2: old
        // 15,50,000 − 2,00,000 = 13,50,000 → 1,12,500 + 1,05,000 = 2,17,500, + 4 % = 2,26,200.
        AllocationSpace space = contributing(flipsWithHra(), Set.of(HRA), Set.of(ComponentCodes.EMPLOYEE_NPS), "0");

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().regime()).isEqualTo(OLD);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("226200"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("HRA", HRA_FACTS, Money.ZERO,
                Money.of("800000"), Optional.of(Money.of("3900")), Money.ZERO));
        assertThat(optimised.changes().contributions()).hasSize(1);
        Optimisation.Change nps = optimised.changes().contributions().getFirst();
        assertThat(nps.component()).isEqualTo("EMPLOYEE_NPS");
        assertThat(nps.facts().kind()).isEqualTo("EMPLOYEE_CONTRIBUTION");
        assertThat(nps.facts().cashNature()).isEqualTo("CASH_OUTFLOW");
        assertThat(nps.facts().feedsInto()).isEqualTo(EMPLOYEE_NPS_FEEDS);
        assertThat(outline(nps.facts().liquidity())).isEqualTo(NPS_2026_27);
        assertThat(nps.current()).isEqualTo(Money.ZERO);
        assertThat(nps.suggested()).isEqualTo(Money.of("210000"));
        assertThat(nps.savingAlone()).contains(Money.ZERO);
        assertThat(nps.cashImpact()).isEqualTo(Money.of("-210000"));
        assertThat(optimised.residualAfter()).isEqualTo(Money.ZERO);
        assertThat(optimised.search().verified()).containsExactly("HRA", "EMPLOYEE_NPS");
        assertThat(optimised.search().discrepancy()).isEmpty();
    }

    @Test
    void optimise_whenTheCashFloorCannotPayTheContribution_leavesItAsEntered() {
        // Given — as entered 24L − 2,92,500 = 21,07,500 in hand; HRA and NPS leave 24L − 2,10,000 − 2,26,200 = 19,63,800,
        // below 20,00,000; HRA alone leaves 24L − 2,88,600 = 21,11,400
        AllocationSpace space = contributing(flipsWithHra(), Set.of(HRA), Set.of(ComponentCodes.EMPLOYEE_NPS),
                "2000000");

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().regime()).isEqualTo(OLD);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("288600"));
        assertThat(optimised.changes().pay()).extracting(Optimisation.Change::component).containsExactly("HRA");
        assertThat(optimised.changes().contributions()).isEmpty();
    }

    @Test
    void optimise_whenTheEmployeesProvidentFundIsHeld_addsTheHeadroomUnderTheCeilingOnTop() {
        // Given — PF 1,00,000 entered, BASIC not movable. Old before: 23,50,000 − 1,00,000 = 22,50,000 → 4,87,500,
        // + 4 % = 5,07,000; new 2,92,500. HRA 8L: old 14,50,000 → 2,47,500, + 4 % = 2,57,400, saving 35,100. The PF's
        // headroom under the ceiling is 1,50,000 − 1,00,000 = 50,000: old 14,00,000 → 2,32,500, + 4 % = 2,41,800.
        AllocationSpace space = contributing(
                flipsWithHraAnd(new SalaryComponent(ComponentCodes.EMPLOYEE_PF, Money.of("100000"))), Set.of(HRA),
                Set.of(ComponentCodes.EMPLOYEE_PF), "0");

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.after().regime()).isEqualTo(OLD);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("241800"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("HRA", HRA_FACTS, Money.ZERO,
                Money.of("800000"), Optional.of(Money.of("35100")), Money.ZERO));
        assertThat(optimised.changes().contributions()).hasSize(1);
        Optimisation.Change pf = optimised.changes().contributions().getFirst();
        assertThat(pf.component()).isEqualTo("EMPLOYEE_PF");
        assertThat(pf.facts().feedsInto()).containsExactly("DED-SCHEDULE-XV");
        assertThat(outline(pf.facts().liquidity())).isEqualTo(EPF_2026_27);
        assertThat(pf.current()).isEqualTo(Money.of("100000"));
        assertThat(pf.suggested()).isEqualTo(Money.of("150000"));
        assertThat(pf.savingAlone()).contains(Money.ZERO);
        assertThat(pf.cashImpact()).isEqualTo(Money.of("-50000"));
    }

    /** {@code components/HRA.json} SALARY; {@code components/LIFE_INSURANCE_PREMIUM.json} QUALIFYING_PAYMENT. */
    @Test
    void optimise_whenAContributionIsNotAnEmployeesOwn_isRefusedNamingItsKind() {
        // Given
        AllocationSpace pay = contributing(flipsWithHra(), Set.of(), Set.of(HRA), "0");
        AllocationSpace premium = contributing(flipsWithHra(), Set.of(),
                Set.of(ComponentCodes.LIFE_INSURANCE_PREMIUM), "0");

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(pay)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HRA is SALARY").hasMessageContaining("not an employee's own contribution");
        assertThatThrownBy(() -> CALCULATOR.optimise(premium)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIFE_INSURANCE_PREMIUM is QUALIFYING_PAYMENT");
    }

    @Test
    void optimise_whenAContributionIsNotInThePack_isRefused() {
        // Given
        AllocationSpace space = contributing(flipsWithHra(), Set.of(), Set.of(new ComponentCode("NO_SUCH_CODE")), "0");

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defines no component NO_SUCH_CODE");
    }

    @Test
    void optimise_whenAContributionIsEnteredTwice_isRefused() {
        // Given
        SalaryComponent nps = new SalaryComponent(ComponentCodes.EMPLOYEE_NPS, Money.of("50000"));
        AllocationSpace space = contributing(flipsWithHraAnd(nps, nps), Set.of(), Set.of(ComponentCodes.EMPLOYEE_NPS),
                "0");

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMPLOYEE_NPS appears more than once");
    }

    /**
     * Basic 16L, HRA 8L entered and held, special 11.2L, rent 9.6L in Mumbai; the employee's provident fund 60,000 and
     * the employer's 1,92,000 (12 %) both follow basic (W-238k1). Every fund probe stays under the 7,50,000 aggregate
     * ({@code PF-AGGCAP-001}), so no history is needed, and a 12 % share of basic is never over 12 % of salary
     * ({@code PF-RPF12-001}).
     */
    private static EngineInputs followedInMumbai() {
        EngineInputs paid = Fixtures.rentingInMumbai("1600000", "1120000", "960000", List.of());
        Employment held = paid.entered().employments().getFirst();
        List<SalaryComponent> components = new ArrayList<>(held.components());
        components.add(new SalaryComponent(HRA, Money.of("800000")));
        components.add(new SalaryComponent(ComponentCodes.EMPLOYEE_PF, Money.of("60000")));
        Employment changed = new Employment(components, held.termsIncludeDearnessAllowance(), held.employerKind(),
                held.dearnessAllowanceCountsForRetirement(), held.companyEmployer());
        return new EngineInputs(paid.taxYear(), paid.regime(), paid.temporal(),
                paid.entered().withEmployments(List.of(changed)).withEmployerContributions(List.of(
                        new EmployerFundContribution(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, Money.of("192000")))),
                paid.assessee(), paid.family(), paid.ledgers(), paid.elections(), paid.capitalGains(), paid.filing());
    }

    @Test
    void optimise_whenTheProvidentFundContributionFollowsAMovableBasic_addsOnTopOfTheFollowingShareToTheCeiling() {
        // Given — old TI = 26,70,000 − 0.02d − min(60,000 + 0.0375d + a, 1,50,000) for basic 16L + d and addition a.
        // Entered: TI 26,10,000 → 5,95,500, + 4 % = 6,19,320 (new 34,45,000 → 6,38,040). Round 1: the addition's
        // 90,000 headroom (TI 25,20,000 → 5,91,240). Round 2: basic to the residual's end, 16L + 11.2L × 16 ÷ 17.92 =
        // 26L: TI 26,50,000 − 1,50,000 = 25,00,000 → 5,62,500, + 4 % = 5,85,000. The follower is then 97,500, so the
        // trim keeps 52,500 of the 90,000 (C2a). Basic alone: new 33,25,000 → 6,00,600, saving 18,720; cash 0 −
        // 1,20,000 (the employer's share) − 37,500 (withheld). The addition alone: TI 25,57,500 → 6,02,940, saving 16,380.
        AllocationSpace space = contributing(followedInMumbai(), Set.of(BASIC), Set.of(ComponentCodes.EMPLOYEE_PF), "0");

        // When
        Optimisation optimised = CALCULATOR.optimise(space);

        // Then
        assertThat(optimised.before().regime()).isEqualTo(OLD);
        assertThat(optimised.before().netPayable()).isEqualTo(Money.of("619320"));
        assertThat(optimised.after().regime()).isEqualTo(OLD);
        assertThat(optimised.after().netPayable()).isEqualTo(Money.of("585000"));
        assertThat(optimised.changes().pay()).containsExactly(new Optimisation.Change("BASIC", BASIC_FACTS,
                Money.of("1600000"), Money.of("2600000"), Optional.of(Money.of("18720")), Money.of("-157500")));
        assertThat(optimised.changes().following()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("liquidity")
                .containsExactly(
                        new Optimisation.FollowingChange("EMPLOYEE_PF", List.of("DED-SCHEDULE-XV"), Money.of("60000"),
                                Money.of("97500"), List.of("BASIC"), NO_LIQUIDITY),
                        new Optimisation.FollowingChange("RECOGNISED_PROVIDENT_FUND", AGGREGATE_FEEDS,
                                Money.of("192000"), Money.of("312000"), List.of("BASIC"), NO_LIQUIDITY));
        assertThat(optimised.changes().contributions()).hasSize(1);
        Optimisation.Change pf = optimised.changes().contributions().getFirst();
        assertThat(pf.component()).isEqualTo("EMPLOYEE_PF");
        assertThat(pf.current()).isEqualTo(Money.of("97500"));
        assertThat(pf.suggested()).isEqualTo(Money.of("150000"));
        assertThat(pf.savingAlone()).contains(Money.of("16380"));
        assertThat(pf.cashImpact()).isEqualTo(Money.of("-52500"));
        assertThat(optimised.residualAfter()).isEqualTo(Money.ZERO);
        assertThat(optimised.search().discrepancy()).isEmpty();
    }

    /** {@code GOVERNMENT_DEFERRED_ANNUITY} is measured on the Government salary; this employer is OTHER (D-154). */
    @Test
    void optimise_whenTheGovernmentDeferredAnnuityIsAddedForAnotherEmployer_isRefusedByTheEngine() {
        // Given
        AllocationSpace space = contributing(flipsWithHra(), Set.of(),
                Set.of(ComponentCodes.GOVERNMENT_DEFERRED_ANNUITY), "0");

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.optimise(space)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is measured on the Government salary");
    }
}
