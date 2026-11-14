package com.thiru.wealthlens.taxengine.optimise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.api.RentRoom;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.RentDeclaration;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.rules.cap.LeastOf;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBaseByLocation;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Rent's worth (W-115, D-670), worked by hand from the TY 2026-27 rows: {@code SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE}
 * ₹50,000; {@code RATE-SLAB-FIRST-SCHEDULE} nil to 2,50,000, 5 % to 5,00,000, 20 % to 10,00,000, 30 % above;
 * {@code RATE-HEALTH-EDUCATION-CESS} 4 %; {@code SAL-HOUSE-RENT-ALLOWANCE} the least of the allowance, rent less 10 % of
 * salary, and 50 % of salary in its eight listed places (AHMEDABAD first by name) or 40 % elsewhere; {@code DED-RENT-PAID}
 * the least of rent less 10 % of total income, ₹5,000 a month and 25 % of total income. Salary for HRA is basic pay.
 * Tax on an income of 10,00,000 or more is 1,12,500 plus 30 % of the excess, with cess 1,17,000 plus 31.2 % of it.
 */
class RentRoomPricingTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)));

    private static final BaseId SALARY = new BaseId("SALARY");

    private static final BaseId LOCATION = new BaseId("LOCATION");

    private static final DateRange WHOLE_YEAR = DateRange.between(TY_2026_27.startsOn(), TY_2026_27.endsOn());

    private static EngineInputs inputs(String regime, String hra, List<Tenancy> homes,
            Optional<RentDeclaration> declaration) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        List<SalaryComponent> pay = hra.equals("0")
                ? List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1000000")))
                : List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1000000")),
                        new SalaryComponent(new ComponentCode("HRA"), Money.of(hra)));
        EnteredFacts entered = EnteredFacts.none()
                .withEmployments(List.of(new Employment(pay, false, EmployerKind.OTHER, false, Optional.empty())))
                .withTenancies(homes)
                .withRentDeclaration(declaration);
        return new EngineInputs(TY_2026_27, new RegimeCode(regime), plain.temporal(), entered, plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    @Test
    void priceRentRoom_whenHraIsReceivedAndNoHomeIsEntered_pricesTheWholeAllowance() {
        // Given basic 10,00,000 and HRA 4,00,000: the allowance binds below 40 % of salary, so every location exempts all of
        // it. Without rent, income 13,50,000 → 1,17,000 + 31.2 % × 3,50,000 = 2,26,200; with it, 9,50,000 → (12,500 +
        // 90,000) × 1.04 = 1,06,600. Rent needed: 4,00,000 + 10 % × 10,00,000.
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", "400000", List.of(), Optional.empty());

        // When
        Optional<RentRoom> room = CALCULATOR.priceRentRoom(inputs);

        // Then a tie reports ANY_OTHER_PLACE, the location that claims least
        assertThat(room).contains(new RentRoom("SAL-HOUSE-RENT-ALLOWANCE", Optional.of("ANY_OTHER_PLACE"), Money.ZERO,
                Money.of("400000"), Money.of("500000"), Money.of("119600")));
    }

    @Test
    void priceRentRoom_whenTheLocationBinds_reportsTheBandThatSavesMost() {
        // Given HRA 6,00,000: 40 % of salary is 4,00,000 and 50 % is 5,00,000. Without rent, income 15,50,000 → 1,17,000 +
        // 31.2 % × 5,50,000 = 2,88,600; at AHMEDABAD, 10,50,000 → 1,17,000 + 31.2 % × 50,000 = 1,32,600; elsewhere
        // 11,50,000 → 1,63,800. Rent needed at AHMEDABAD: 5,00,000 + 1,00,000.
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", "600000", List.of(), Optional.empty());

        // When
        Optional<RentRoom> room = CALCULATOR.priceRentRoom(inputs);

        // Then
        assertThat(room).contains(new RentRoom("SAL-HOUSE-RENT-ALLOWANCE", Optional.of("AHMEDABAD"), Money.ZERO,
                Money.of("500000"), Money.of("600000"), Money.of("156000")));
    }

    @Test
    void priceRentRoom_whenAHomeIsEnteredWithLowRent_raisesItsRentAtItsOwnLocation() {
        // Given HRA 4,00,000 and rent 2,00,000 for the year: exempt min(4,00,000, 2,00,000 − 1,00,000, 4,00,000) =
        // 1,00,000, income 12,50,000 → 1,17,000 + 31.2 % × 2,50,000 = 1,95,000; raised, 4,00,000 exempt → 1,06,600
        List<Tenancy> home = List.of(new Tenancy(WHOLE_YEAR, "ANY_OTHER_PLACE", false, Money.of("200000")));
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", "400000", home, Optional.empty());

        // When
        Optional<RentRoom> room = CALCULATOR.priceRentRoom(inputs);

        // Then
        assertThat(room).contains(new RentRoom("SAL-HOUSE-RENT-ALLOWANCE", Optional.empty(), Money.of("100000"),
                Money.of("300000"), Money.of("500000"), Money.of("88400")));
    }

    @Test
    void priceRentRoom_whenNoHraIsReceived_pricesRentPaidUnderSection134() {
        // Given basic 10,00,000 alone: income 9,50,000 → 1,06,600. s.134 takes the least of ₹5,000 × 12 = 60,000 and
        // 25 % × 9,50,000, so income 8,90,000 → (12,500 + 78,000) × 1.04 = 94,120. Rent needed: 60,000 + 10 % × 9,50,000.
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", "0", List.of(), Optional.empty());

        // When
        Optional<RentRoom> room = CALCULATOR.priceRentRoom(inputs);

        // Then
        assertThat(room).contains(new RentRoom("DED-RENT-PAID", Optional.empty(), Money.ZERO, Money.of("60000"),
                Money.of("155000"), Money.of("12480")));
    }

    @Test
    void priceRentRoom_whenTheDeclarationStatesAnOwnershipBar_isEmpty() {
        // Given a home owned at the place of work (s.134(4)(a)(i)): s.134 allows nothing whatever the rent
        List<Tenancy> home = List.of(new Tenancy(WHOLE_YEAR, "ANY_OTHER_PLACE", false, Money.of("120000")));
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", "0", home,
                Optional.of(new RentDeclaration(12, true, false)));

        // When / Then
        assertThat(CALCULATOR.priceRentRoom(inputs)).isEmpty();
    }

    @Test
    void priceRentRoom_whenTheRegimeAllowsNoHraExemption_isEmpty() {
        // Given the new regime, which excludes Sch. III Sl. 11 (s.202(2)(a)(i))
        EngineInputs inputs = inputs("NEW_TAX_REGIME", "400000", List.of(), Optional.empty());

        // When / Then
        assertThat(CALCULATOR.priceRentRoom(inputs)).isEmpty();
    }

    @Test
    void priceRentRoom_whenHomesAreEnteredWithoutADeclaration_declaresTheMonthsTheRentedOneOccupies() {
        // Given an owned home April – September, then one rented October – March, no HRA and no declaration: the filled
        // declaration states six months, so s.134 takes ₹5,000 × 6 = 30,000; income 9,20,000 → (12,500 + 84,000) × 1.04 =
        // 1,00,360 against 1,06,600. Rent needed: 30,000 + 10 % × 9,50,000.
        List<Tenancy> homes = List.of(
                new Tenancy(DateRange.between(TY_2026_27.startsOn(), TY_2026_27.startsOn().plusMonths(6).minusDays(1)),
                        "ANY_OTHER_PLACE", true, Money.ZERO),
                new Tenancy(DateRange.between(TY_2026_27.startsOn().plusMonths(6), TY_2026_27.endsOn()), "ANY_OTHER_PLACE",
                        false, Money.of("120000")));
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", "0", homes, Optional.empty());

        // When
        Optional<RentRoom> room = CALCULATOR.priceRentRoom(inputs);

        // Then
        assertThat(room).contains(new RentRoom("DED-RENT-PAID", Optional.empty(), Money.ZERO, Money.of("30000"),
                Money.of("125000"), Money.of("6240")));
    }

    @Test
    void priceRentRoom_whenThereIsNoIncome_isEmpty() {
        // Given nothing entered: no rent can be filled against no income
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(TY_2026_27, new RegimeCode("FIRST_SCHEDULE_RATES"), plain.temporal(),
                EnteredFacts.none(), plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());

        // When / Then
        assertThat(CALCULATOR.priceRentRoom(inputs)).isEmpty();
    }

    @Test
    void locations_whenTheCapHasBands_putsAnyOtherPlaceFirstThenTheFirstPlaceOfEachBandByName() {
        // Given
        LeastOf cap = new LeastOf(List.of(new LeastOf.Limb("by location", new PercentOfBaseByLocation(SALARY, LOCATION,
                List.of(new PercentOfBaseByLocation.Band(Set.of("PUNE", "DELHI"), Rate.percent("50")),
                        new PercentOfBaseByLocation.Band(Set.of("SURAT"), Rate.percent("45"))),
                Rate.percent("40")))));

        // When / Then
        assertThat(RentRoomPricing.locations(cap)).containsExactly("ANY_OTHER_PLACE", "DELHI", "SURAT");
    }

    @Test
    void locations_whenTheCapHasNoLimbByLocation_isRefused() {
        // Given
        LeastOf cap = new LeastOf(List.of(new LeastOf.Limb("salary", new PercentOfBase(Rate.percent("40"), SALARY))));

        // When / Then
        assertThatThrownBy(() -> RentRoomPricing.locations(cap)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no limb by location");
    }

    @Test
    void rentLimb_whenTheCapHasNoLimbOnRent_isRefusedNamingTheRow() {
        // Given
        LeastOf cap = new LeastOf(List.of(new LeastOf.Limb("salary", new PercentOfBase(Rate.percent("40"), SALARY))));

        // When / Then
        assertThatThrownBy(() -> RentRoomPricing.rentLimb(cap, new RuleId("SAL-HOUSE-RENT-ALLOWANCE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAL-HOUSE-RENT-ALLOWANCE has no limb on rent");
    }

    @Test
    void belowFillingRent_whenTheRentNeededReachesTheFillingRent_isRefused() {
        // Given / When / Then — a rent limb that still binds would make the figure the filling rent's, not the row's
        assertThatThrownBy(() -> RentRoomPricing.belowFillingRent(Money.of("500000"), Money.of("500000"),
                new RuleId("DED-RENT-PAID"))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DED-RENT-PAID's rent limb still bound");
    }
}
