package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EducationLoanInterest;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.CapitalGainsHarness;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The calendar's measured costs (W-114, D-665), on {@code DR 13} T13-1: 1,75,000 of basic pay and ₹11,00,000 of long-term
 * listed-equity gain make a bill of 87,750 ({@code plans/W-089.md}), the salary alone within the nil band. Nothing is paid
 * and the return is due 31-7-2027; every cost is measured as of that day.
 *
 * <p>Worked by hand from {@code INT-ADVANCE-SHORTFALL} (1 % a month or part from 1 April where under 90 % is paid),
 * {@code INT-DEFERMENT} (15 / 45 / 75 / 100 % at 3, 3, 3 and 1 %; no interest at June or September where 12 or 36 % is
 * paid; relief where the tax on income arising after a date is paid after it by 31 March), {@code INT-LATE-RETURN} and
 * {@code INT-CALCULATION} (base down to ₹100, part of a month whole). Nothing paid: s.424 87,700 × 1 % × 4 (April – July)
 * = 3,508; s.425 393 + 1,182 + 1,974 + 877 = 4,426; 7,934 in all.
 */
class DraftCalendarServiceTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final DraftCalendarService SERVICE = new DraftCalendarService(null, null, CALCULATOR, null);

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final LocalDate DUE = LocalDate.of(2027, 7, 31);

    static EngineInputs inputs(LocalDate soldOn, Optional<LocalDate> furnishedOn) {
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), LocalDate.of(2024, 4, 1),
                soldOn, SideVenue.UNRECORDED, new SideVenue(Optional.of("NSE"), Optional.empty()));
        CapitalGainsInput gains = CapitalGainsHarness.inputOf(new CapitalGainsInput.Bucket(Money.of("1600000"),
                Money.of("500000"), List.of(), facts, Optional.empty()));
        Employment basicPay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("175000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, plain.regime(), TemporalContext.forTaxYear(TY_2026_27, DUE),
                EnteredFacts.none().withEmployments(List.of(basicPay)), plain.assessee(), plain.family(), plain.ledgers(),
                plain.elections(), gains,
                new FilingFacts(DUE, furnishedOn, List.of(), Money.ZERO, List.of(), Map.of(), Optional.empty()));
    }

    private static DraftCalendar.Entry entry(List<DraftCalendar.Entry> entries, LocalDate on, DraftCalendar.Kind kind) {
        return entries.stream().filter(entry -> entry.on().equals(on) && entry.kind() == kind).findFirst().orElseThrow();
    }

    @Test
    void entries_whenNothingIsPaid_costsEachInstalmentWhatPayingItsArrearsThenSaves() {
        // Given — sold 1-9-2026
        List<DraftCalendar.Entry> entries = SERVICE.entries(inputs(LocalDate.of(2026, 9, 1), Optional.empty()), DUE);

        // Then — June, 13,162.50 paid 15-6: s.424 74,500 × 4 % = 2,980; June harboured (12 %); 26,300, 52,600 and 74,500 at
        // 3, 3, 1 % = 789 + 1,578 + 745; 6,092, so 7,934 − 6,092 = 1,842. September, 39,487.50: s.424 48,200 × 4 % = 1,928;
        // 393 + nil + 26,300 × 3 % = 789 + 48,200 × 1 % = 482; 3,592, so 4,342. December, 65,812.50: s.424 21,900 × 4 % = 876;
        // 393 + 1,182 + nil + 219; 2,670, so 5,264. March, 87,750: no s.424; June relieved (all paid after it by 31 March),
        // 1,182 + 1,974; 3,156, so 4,778
        LocalDate june = LocalDate.of(2026, 6, 15);
        assertThat(entry(entries, june, DraftCalendar.Kind.ADVANCE_TAX_INSTALMENT).amountToPay()).contains(Money.of("13162.50"));
        assertThat(entry(entries, june, DraftCalendar.Kind.ADVANCE_TAX_INSTALMENT).costOfMissing()).isEqualTo(Money.of("1842"));
        assertThat(entry(entries, LocalDate.of(2026, 9, 15), DraftCalendar.Kind.ADVANCE_TAX_INSTALMENT).costOfMissing())
                .isEqualTo(Money.of("4342"));
        assertThat(entry(entries, LocalDate.of(2026, 12, 15), DraftCalendar.Kind.ADVANCE_TAX_INSTALMENT).costOfMissing())
                .isEqualTo(Money.of("5264"));
        assertThat(entry(entries, LocalDate.of(2027, 3, 15), DraftCalendar.Kind.ADVANCE_TAX_INSTALMENT).costOfMissing())
                .isEqualTo(Money.of("4778"));
    }

    @Test
    void entries_whenAGainAroseAfterAnInstalmentDate_namesThirtyFirstMarchAndWhatPayingByThenSaves() {
        // Given — sold 1-9-2026, after 15 June
        List<DraftCalendar.Entry> entries = SERVICE.entries(inputs(LocalDate.of(2026, 9, 1), Optional.empty()), DUE);

        // Then — 87,750 paid 31-3-2027: no s.424 (31 March counts as advance tax, ADV-MAR31-001); June relieved; 1,182 +
        // 1,974 + March 87,700 × 1 % = 877; 4,033, so 7,934 − 4,033 = 3,901
        DraftCalendar.Entry relief = entry(entries, LocalDate.of(2027, 3, 31), DraftCalendar.Kind.DEFERMENT_RELIEF_PAID_BY);
        assertThat(relief.rule()).isEqualTo("INT-DEFERMENT");
        assertThat(relief.amountToPay()).contains(Money.of("87750"));
        assertThat(relief.costOfMissing()).isEqualTo(Money.of("3901"));
    }

    @Test
    void entries_whenTheGainAroseBeforeTheFirstInstalmentDate_namesNoReliefDay() {
        // Given — sold 1-6-2026, before 15 June: nothing arises after a date
        List<DraftCalendar.Entry> entries = SERVICE.entries(inputs(LocalDate.of(2026, 6, 1), Optional.empty()), DUE);

        // Then
        assertThat(entries).noneMatch(entry -> entry.kind() == DraftCalendar.Kind.DEFERMENT_RELIEF_PAID_BY);
    }

    @Test
    void entries_whenTheReturnIsNotYetFurnished_costsADayLate() {
        // Given / When
        List<DraftCalendar.Entry> entries = SERVICE.entries(inputs(LocalDate.of(2026, 9, 1), Optional.empty()), DUE);

        // Then — furnished 1-8-2027: s.423 87,700 × 1 % = 877, s.424 a fifth month 4,385, s.428 5,000 on income over 5 lakh;
        // 877 + 4,385 + 4,426 + 5,000 = 14,688, so 14,688 − 7,934 = 6,754
        DraftCalendar.Entry due = entry(entries, DUE, DraftCalendar.Kind.RETURN_DUE);
        assertThat(due.costOfMissing()).isEqualTo(Money.of("6754"));
        assertThat(due.furnishedOn()).isEmpty();
        assertThat(due.amountToPay()).contains(Money.of("95680"));
    }

    @Test
    void entries_whenTheReturnWasFurnishedLate_showsTheDayAndWhatItCost() {
        // Given — furnished 1-8-2027
        LocalDate furnished = LocalDate.of(2027, 8, 1);

        // When
        List<DraftCalendar.Entry> entries = SERVICE.entries(inputs(LocalDate.of(2026, 9, 1), Optional.of(furnished)), furnished);

        // Then — what actually ran, 14,688, less furnishing on time, 7,934
        DraftCalendar.Entry due = entry(entries, DUE, DraftCalendar.Kind.RETURN_DUE);
        assertThat(due.furnishedOn()).contains(furnished);
        assertThat(due.costOfMissing()).isEqualTo(Money.of("6754"));
    }

    @Test
    void entries_whenAnInstalmentsArrearsArePaid_costsItNothing() {
        // Given — 65,812.50 paid on 15-12-2026
        EngineInputs unpaid = inputs(LocalDate.of(2026, 9, 1), Optional.empty());
        FilingFacts filing = new FilingFacts(DUE, Optional.empty(),
                List.of(new FilingFacts.Payment(LocalDate.of(2026, 12, 15), Money.of("65812.50"))), Money.ZERO, List.of(),
                Map.of(), Optional.empty());
        EngineInputs paid = new EngineInputs(unpaid.taxYear(), unpaid.regime(), unpaid.temporal(), unpaid.entered(),
                unpaid.assessee(), unpaid.family(), unpaid.ledgers(), unpaid.elections(), unpaid.capitalGains(), filing);

        // When
        DraftCalendar.Entry december = entry(SERVICE.entries(paid, DUE), LocalDate.of(2026, 12, 15),
                DraftCalendar.Kind.ADVANCE_TAX_INSTALMENT);

        // Then
        assertThat(december.paidByThen()).contains(Money.of("65812.50"));
        assertThat(december.amountToPay()).contains(Money.ZERO);
        assertThat(december.costOfMissing()).isEqualTo(Money.ZERO);
    }

    /** Basic pay of 10,00,000 and 50,000 of tuition fees, under {@code regime}: DeductionRoomPricingTest's case. */
    private static EngineInputs tuitionPayer(String regime) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment basicPay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1000000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        return new EngineInputs(TY_2026_27, new RegimeCode(regime), TemporalContext.forTaxYear(TY_2026_27, DUE),
                EnteredFacts.none().withEmployments(List.of(basicPay)).withQualifyingPayments(
                        List.of(new QualifyingPayment(new ComponentCode("TUITION_FEES"), Money.of("50000")))),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                new FilingFacts(DUE, Optional.empty(), List.of(), Money.ZERO, List.of(), Map.of(), Optional.empty()));
    }

    @Test
    void entries_whenScheduleXvHasRoomInTheOldRegime_namesTheYearsLastDayAndTheTaxItSaves() {
        // Given / When
        List<DraftCalendar.Entry> entries = SERVICE.entries(tuitionPayer("FIRST_SCHEDULE_RATES"), DUE);

        // Then — DeductionRoomPricingTest: 1,00,000 of room saves 20,800; 50,000 counted
        DraftCalendar.Entry room = entry(entries, LocalDate.of(2027, 3, 31), DraftCalendar.Kind.DEDUCTION_PAID_BY);
        assertThat(room.rule()).isEqualTo("DED-SCHEDULE-XV");
        assertThat(room.amountToPay()).contains(Money.of("100000"));
        assertThat(room.paidByThen()).contains(Money.of("50000"));
        assertThat(room.costOfMissing()).isEqualTo(Money.of("20800"));
    }

    @Test
    void entries_whenTheAssesseeHasNoHealthCoverInTheOldRegime_namesItsRoomOnTheYearsLastDay() {
        // Given / When
        List<DraftCalendar.Entry> entries = SERVICE.entries(tuitionPayer("FIRST_SCHEDULE_RATES"), DUE);

        // Then — DeductionRoomPricingTest: 25,000 of premium room, 9,00,000 → 8,75,000, saving 5,200
        assertThat(entries).filteredOn(entry -> entry.rule().equals("DED-HEALTH-FAMILY")).singleElement().satisfies(health -> {
            assertThat(health.on()).isEqualTo(LocalDate.of(2027, 3, 31));
            assertThat(health.kind()).isEqualTo(DraftCalendar.Kind.DEDUCTION_PAID_BY);
            assertThat(health.amountToPay()).contains(Money.of("25000"));
            assertThat(health.costOfMissing()).isEqualTo(Money.of("5200"));
        });
    }

    @Test
    void entries_whenTheRegimeAllowsNoScheduleXvDeduction_namesNoDeductionDay() {
        // Given / When / Then
        assertThat(SERVICE.entries(tuitionPayer("NEW_TAX_REGIME"), DUE))
                .noneMatch(entry -> entry.kind() == DraftCalendar.Kind.DEDUCTION_PAID_BY);
    }

    /** Basic pay of 15,75,000 and {@code interest} of education-loan interest, under {@code regime}: RegimeElectionTest's case. */
    private static EngineInputs borrower(String regime, String interest) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment basicPay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1575000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        List<EducationLoanInterest> loans = interest == null ? List.of()
                : List.of(new EducationLoanInterest(Money.of(interest), TY_2026_27));
        EnteredFacts entered = new EnteredFacts(List.of(basicPay), List.of(), List.of(), List.of(), Optional.empty(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), loans, List.of(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Optional.empty(), List.of());
        return new EngineInputs(TY_2026_27, new RegimeCode(regime), TemporalContext.forTaxYear(TY_2026_27, DUE), entered,
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                new FilingFacts(DUE, Optional.empty(), List.of(), Money.ZERO, List.of(), Map.of(), Optional.empty()));
    }

    @Test
    void entries_whenOptingOutCostsLess_namesTheDueDateAndTheTaxStayingCosts() {
        // Given / When
        List<DraftCalendar.Entry> entries = SERVICE.entries(borrower("NEW_TAX_REGIME", "600000"), DUE);

        // Then — RegimeElectionTest: 1,09,200 against 1,01,400, so 7,800
        DraftCalendar.Entry election = entry(entries, DUE, DraftCalendar.Kind.REGIME_ELECTION);
        assertThat(election.rule()).isEqualTo("FIRST_SCHEDULE_RATES");
        assertThat(election.amountToPay()).isEmpty();
        assertThat(election.costOfMissing()).isEqualTo(Money.of("7800"));
    }

    @Test
    void entries_whenOptingOutCostsMore_namesNoElection() {
        // Given / When / Then — RegimeElectionTest: 2,80,800 against 1,09,200
        assertThat(SERVICE.entries(borrower("NEW_TAX_REGIME", null), DUE))
                .noneMatch(entry -> entry.kind() == DraftCalendar.Kind.REGIME_ELECTION);
    }

    @Test
    void entries_whenTheDraftAlreadyElectedTheCheaperRegime_namesNoElection() {
        // Given / When / Then — the draft computed under FIRST_SCHEDULE_RATES, its election made
        assertThat(SERVICE.entries(borrower("FIRST_SCHEDULE_RATES", "600000"), DUE))
                .noneMatch(entry -> entry.kind() == DraftCalendar.Kind.REGIME_ELECTION);
    }
}
