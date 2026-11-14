package com.thiru.wealthlens.taxengine.optimise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.api.DeductionRoom;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Schedule XV's room and its price (W-114a, D-666), worked by hand from the TY 2026-27 rows: {@code SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE}
 * ₹50,000; {@code RATE-SLAB-FIRST-SCHEDULE} nil to 2,50,000, 5 % to 5,00,000, 20 % to 10,00,000, 30 % above;
 * {@code RATE-HEALTH-EDUCATION-CESS} 4 %; {@code DED-SCHEDULE-XV}'s ceiling ₹1,50,000. Basic pay 10,00,000 less 50,000 is
 * 9,50,000 before Schedule XV, so every rupee of room falls in the 20 % band: it saves 20 % with 4 % cess on it.
 */
class DeductionRoomPricingTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final ShippedRulePacks PACKS = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27));

    private static final TaxCalculator CALCULATOR = new TaxCalculator(PACKS);

    private static final ComponentCode TUITION_FEES = new ComponentCode("TUITION_FEES");

    private static EngineInputs inputs(String regime, List<QualifyingPayment> payments) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment basicPay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1000000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        return new EngineInputs(TY_2026_27, new RegimeCode(regime), plain.temporal(),
                EnteredFacts.none().withEmployments(List.of(basicPay)).withQualifyingPayments(payments), plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    @Test
    void priceDeductionRoom_whenPartOfTheCeilingIsUsedInTheOldRegime_pricesTheRestAtTheMarginalRate() {
        // Given — 50,000 of tuition fees
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", List.of(new QualifyingPayment(TUITION_FEES, Money.of("50000"))));

        // When
        List<DeductionRoom> rooms = CALCULATOR.priceDeductionRoom(inputs);

        // Then — 1,50,000 − 50,000 = 1,00,000 of room; total income 9,00,000 → 8,00,000: 92,500 + 3,700 = 96,200 against
        // 72,500 + 2,900 = 75,400, so 20,800
        assertThat(rooms).containsExactly(new DeductionRoom("DED-SCHEDULE-XV", Money.of("50000"), Money.of("100000"), Money.of("20800")));
    }

    @Test
    void priceDeductionRoom_whenNothingIsPaidInTheOldRegime_pricesTheWholeCeiling() {
        // Given / When
        List<DeductionRoom> rooms = CALCULATOR.priceDeductionRoom(inputs("FIRST_SCHEDULE_RATES", List.of()));

        // Then — 9,50,000 → 8,00,000: 1,02,500 + 4,100 = 1,06,600 against 75,400, so 31,200
        assertThat(rooms).containsExactly(new DeductionRoom("DED-SCHEDULE-XV", Money.ZERO, Money.of("150000"), Money.of("31200")));
    }

    @Test
    void priceDeductionRoom_whenTheCeilingIsReached_hasNoRoom() {
        // Given / When / Then
        assertThat(CALCULATOR.priceDeductionRoom(inputs("FIRST_SCHEDULE_RATES",
                List.of(new QualifyingPayment(TUITION_FEES, Money.of("150000")))))).isEmpty();
    }

    @Test
    void priceDeductionRoom_whenTheRegimeAllowsNoScheduleXvDeduction_savesNothing() {
        // Given — the default regime: the row's line is nil, so the whole ceiling is room, and filling it changes no bill
        EngineInputs inputs = inputs("NEW_TAX_REGIME", List.of(new QualifyingPayment(TUITION_FEES, Money.of("50000"))));

        // When / Then
        assertThat(CALCULATOR.priceDeductionRoom(inputs))
                .containsExactly(new DeductionRoom("DED-SCHEDULE-XV", Money.ZERO, Money.of("150000"), Money.ZERO));
    }

    @Test
    void carrier_whenTheFirstContributorIsSubCapped_passesItOver() {
        // Given — DEFERRED_ANNUITY first with a 10,000 sub-cap of its own, then NOTIFIED_PROVIDENT_FUND without one
        ComponentCode deferredAnnuity = new ComponentCode("DEFERRED_ANNUITY");
        ComponentCode notifiedFund = new ComponentCode("NOTIFIED_PROVIDENT_FUND");
        AggregateWithCeiling cap = new AggregateWithCeiling(List.of(deferredAnnuity, notifiedFund), Money.of("150000"),
                Map.of(deferredAnnuity, new Fixed(Money.of("10000"))));

        // When
        ComponentCode chosen = DeductionRoomPricing.carrier(PACKS.load(TY_2026_27).components(), TY_2026_27, cap,
                new RuleId("DED-SCHEDULE-XV"));

        // Then
        assertThat(chosen).isEqualTo(notifiedFund);
    }

    @Test
    void carrier_whenALifeInsurancePremiumComesFirst_passesItOver() {
        // Given — a premium is entered with its policy, never as a payment (D-134)
        AggregateWithCeiling cap = new AggregateWithCeiling(
                List.of(new ComponentCode("LIFE_INSURANCE_PREMIUM"), new ComponentCode("DEFERRED_ANNUITY")), Money.of("150000"),
                Map.of());

        // When / Then
        assertThat(DeductionRoomPricing.carrier(PACKS.load(TY_2026_27).components(), TY_2026_27, cap,
                new RuleId("DED-SCHEDULE-XV"))).isEqualTo(new ComponentCode("DEFERRED_ANNUITY"));
    }

    @Test
    void carrier_whenEveryContributorIsSubCapped_isRefusedNamingTheRow() {
        // Given
        ComponentCode deferredAnnuity = new ComponentCode("DEFERRED_ANNUITY");
        AggregateWithCeiling cap = new AggregateWithCeiling(List.of(deferredAnnuity), Money.of("150000"),
                Map.of(deferredAnnuity, new Fixed(Money.of("10000"))));

        // When / Then
        assertThatThrownBy(() -> DeductionRoomPricing.carrier(PACKS.load(TY_2026_27).components(), TY_2026_27, cap,
                new RuleId("DED-SCHEDULE-XV"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("DED-SCHEDULE-XV");
    }

    @Test
    void onlyMember_whenTheSetHoldsTwoRows_isRefusedRatherThanChoosing() {
        // Given / When / Then — a row is chosen by its conditions inside the run, never here (D-252)
        assertThatThrownBy(() -> DeductionRoomPricing.onlyMember(List.of(new RuleId("DED-SCHEDULE-XV"), new RuleId("DED-OTHER"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("D-252");
    }
}
