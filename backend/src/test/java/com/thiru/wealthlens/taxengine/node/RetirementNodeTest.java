package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.LeaveAtRetirement;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.input.MonthOfPay;
import com.thiru.wealthlens.taxengine.input.RetirementEntry;
import com.thiru.wealthlens.taxengine.input.RetirementPayment;
import com.thiru.wealthlens.taxengine.input.Separation;
import com.thiru.wealthlens.taxengine.input.StatutoryEntitlement;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.LeastOf;
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
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The retirement and termination deductions (S2 s.19(1) Table Sl. 3–13 as built; 1961 s.10(10)–(10C); D-103). Each
 * figure is worked by hand from the Acts: ₹500000 for voluntary retirement (Sl. 12; s.10(10C) "five lakh rupees"),
 * one-third or one-half of the pension's commuted value (Sl. 8; s.10(10A)(ii)), the rest in full.
 */
class RetirementNodeTest {

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private record Paid(ComponentCode component, String amount) {
    }

    private static NodeResult run(int year, RegimeCode regime, List<Paid> paid, List<RetirementPayment> payments) {
        return run(year, regime, paid, payments, LedgerSnapshot.empty());
    }

    private static NodeResult run(int year, RegimeCode regime, List<Paid> paid, List<RetirementPayment> payments,
            LedgerSnapshot ledgers) {
        return run(year, regime, paid, payments, ledgers, false);
    }

    private static NodeResult run(int year, RegimeCode regime, List<Paid> paid, List<RetirementPayment> payments,
            LedgerSnapshot ledgers, boolean termsIncludeDearnessAllowance) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), termsIncludeDearnessAllowance, EmployerKind.OTHER, false, Optional.empty())),
                        List.of(), List.of(), List.of(), Optional.empty(), payments, List.of(), List.of(), List.of(), List.of(), List.of()),
                plain.assessee(), plain.family(), ledgers, plain.elections(), plain.capitalGains(), plain.filing());
        List<Line> lines = new ArrayList<>();
        for (Paid line : paid) {
            lines.add(new Line(0, line.component(), Money.of(line.amount())));
        }
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of(HeadCode.SALARIES, lines)))
                .run(new RetirementNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenGovernmentGratuity_deductsItAll(int year) {
        // When — Sl. 3; s.10(10)(i): entire amount
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, List.of(new Paid(ComponentCodes.GRATUITY, "1000000")),
                List.of(RetirementPayment.of(ComponentCodes.GRATUITY, RetirementEntry.GOVERNMENT_GRATUITY)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1000000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.RET_GRATUITY_GOVERNMENT);
            assertThat(line.kind()).isEqualTo(StepKind.DEDUCTION);
            assertThat(line.provenance().verificationLevel()).isEqualTo(year == 2025
                    ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenVoluntaryRetirementAboveTheLimit_deductsTheLimit(int year) {
        // When — least of 8,00,000 and 5,00,000
        NodeResult result = run(year, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.VRS_COMPENSATION, "800000")),
                List.of(RetirementPayment.voluntaryRetirement(ComponentCodes.VRS_COMPENSATION, false)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("500000")));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).contains("limit"));
    }

    @Test
    void evaluate_whenVoluntaryRetirementWasAllowedInAnotherYear_givesANilLineNamingIt() {
        // Given — s.19(2)(e)(ii): once only
        LedgerSnapshot claimed = new LedgerSnapshot(Map.of(), Optional.of(TaxYearId.of(2021)), Money.ZERO,
                Money.ZERO, 0, Map.of(), Optional.empty());

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.VRS_COMPENSATION, "300000")),
                List.of(RetirementPayment.voluntaryRetirement(ComponentCodes.VRS_COMPENSATION, false)), claimed);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> assertThat(line.limitedBy())
                .hasValueSatisfying(why -> assertThat(why).contains("2021-22")));
    }

    @Test
    void evaluate_whenVoluntaryRetirementWasAllowedInThisYear_isStillDeducted() {
        // Given — a recomputation of the year the deduction was allowed in
        LedgerSnapshot thisYear = new LedgerSnapshot(Map.of(), Optional.of(TaxYearId.of(2026)), Money.ZERO,
                Money.ZERO, 0, Map.of(), Optional.empty());

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.VRS_COMPENSATION, "300000")),
                List.of(RetirementPayment.voluntaryRetirement(ComponentCodes.VRS_COMPENSATION, false)), thisYear);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("300000")));
    }

    @Test
    void evaluate_whenArrearsReliefWasAllowedOnTheVoluntaryRetirementAmount_givesANilLine() {
        // When — s.19(2)(e)(iii); 1961 s.10(10C) third proviso
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.VRS_COMPENSATION, "300000")),
                List.of(RetirementPayment.voluntaryRetirement(ComponentCodes.VRS_COMPENSATION, true)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> assertThat(line.limitedBy())
                .hasValueSatisfying(why -> assertThat(why).contains("relief")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAnOtherEmployerCommutesWithGratuity_deductsAThirdOfTheCommutedValue(int year) {
        // When — commuted value of the whole pension 9,00,000; 4,00,000 received; gratuity 1,00,000 also received:
        // least of 4,00,000 and 9,00,000 ÷ 3 = 3,00,000; gratuity in full → 4,00,000
        NodeResult result = run(year, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.COMMUTED_PENSION, "400000"),
                        new Paid(ComponentCodes.GRATUITY, "100000")),
                List.of(RetirementPayment.otherEmployerCommutation(ComponentCodes.COMMUTED_PENSION,
                                Money.of("900000")),
                        RetirementPayment.of(ComponentCodes.GRATUITY, RetirementEntry.GOVERNMENT_GRATUITY)));

        // Then
        assertThat(result.trace()).extracting(line -> line.out().asMoney())
                .containsExactly(Money.of("300000"), Money.of("100000"));
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("400000")));
    }

    @Test
    void evaluate_whenAnOtherEmployerCommutesWithoutGratuity_deductsUpToHalfTheCommutedValue() {
        // When — least of 4,00,000 and 9,00,000 ÷ 2 = 4,50,000
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.COMMUTED_PENSION, "400000")),
                List.of(RetirementPayment.otherEmployerCommutation(ComponentCodes.COMMUTED_PENSION,
                        Money.of("900000"))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("400000")));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).contains("payment received"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenInTheNewRegime_stillDeducts(int year) {
        // When — s.202(2)(a)(iv) excludes s.19(1) Sl. 1 only; s.115BAC(2)(i) excludes none of s.10(10)–(10C)
        NodeResult result = run(year, NEW_TAX_REGIME,
                List.of(new Paid(ComponentCodes.LEAVE_ENCASHMENT, "600000")),
                List.of(RetirementPayment.of(ComponentCodes.LEAVE_ENCASHMENT,
                        RetirementEntry.GOVERNMENT_LEAVE_ENCASHMENT)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("600000")));
    }

    @Test
    void evaluate_whenGratuityIsPaidWithoutItsEntry_isRefusedAskingForIt() {
        // When / Then — which Table entry applies turns on the scheme; taxing it in full would be a guess (D-109: every
        // entry is built, so the entry is asked for rather than an item named)
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES,
                        List.of(new Paid(ComponentCodes.GRATUITY, "500000")), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GRATUITY")
                .hasMessageContaining("enter it");
    }

    @Test
    void evaluate_whenAnEntryDoesNotFitItsComponent_isRefused() {
        // When / Then — gratuity cannot be voluntary retirement compensation
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES,
                        List.of(new Paid(ComponentCodes.GRATUITY, "500000")),
                        List.of(RetirementPayment.voluntaryRetirement(ComponentCodes.GRATUITY, false))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VOLUNTARY_RETIREMENT");
    }

    @Test
    void evaluate_whenAComponentIsEnteredTwice_isRefused() {
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES,
                        List.of(new Paid(ComponentCodes.GRATUITY, "500000")),
                        List.of(RetirementPayment.of(ComponentCodes.GRATUITY, RetirementEntry.GOVERNMENT_GRATUITY),
                                RetirementPayment.of(ComponentCodes.GRATUITY,
                                        RetirementEntry.DEFENCE_RETIRING_GRATUITY))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void evaluate_whenOnlyNoticePayIsPaid_deductsNothingAndSaysNothing() {
        // When — profits in lieu of salary (s.18(1)(a)); no entry of s.19(1) deducts it
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.NOTICE_PAY, "100000")), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }

    // ---------------------------------------------------------------- other gratuity and leave (D-106)

    private static final LedgerSnapshot NOTHING_BEFORE = LedgerSnapshot.empty();

    /** Retirement on 30 June of {@code year}, after 25 completed years; each of the ten months pays these lines. */
    private static Separation retiringInJune(int year, Map<ComponentCode, Money> monthly) {
        LocalDate separatedOn = LocalDate.of(year, 6, 30);
        List<MonthOfPay> months = new ArrayList<>();
        for (int back = 10; back >= 1; back--) {
            months.add(new MonthOfPay(java.time.YearMonth.from(separatedOn).minusMonths(back), monthly));
        }
        return new Separation(separatedOn.minusYears(25).minusMonths(3), separatedOn, months);
    }

    private static LedgerSnapshot allowedBefore(String gratuity, String leave) {
        return new LedgerSnapshot(Map.of(), Optional.empty(), Money.of(gratuity), Money.of(leave), 0, Map.of(),
                Optional.empty());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOtherGratuity_deductsHalfAMonthsAverageSalaryForEachCompletedYear(int year) {
        // When — 25 completed years on an average of 1,00,000: least of 15,00,000, 20,00,000 and
        // 1/2 × 1,00,000 × 25 = 12,50,000
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, List.of(new Paid(ComponentCodes.GRATUITY, "1500000")),
                List.of(RetirementPayment.otherGratuity(ComponentCodes.GRATUITY,
                        retiringInJune(year, Map.of(ComponentCodes.BASIC, Money.of("100000"))))),
                NOTHING_BEFORE);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1250000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.RET_GRATUITY_OTHER);
            assertThat(line.limitedBy()).contains("half a month's salary for each completed year");
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenEarlierYearsUsedPartOfTheGratuityLimit_deductsOnlyWhatRemains(int year) {
        // When — s.19(2)(a): A − B = 20,00,000 − 10,00,000 = 10,00,000, below the 12,50,000 Sl. 6 allows
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, List.of(new Paid(ComponentCodes.GRATUITY, "1500000")),
                List.of(RetirementPayment.otherGratuity(ComponentCodes.GRATUITY,
                        retiringInJune(year, Map.of(ComponentCodes.BASIC, Money.of("100000"))))),
                allowedBefore("1000000", "0"));

        // Then — the aggregate binds, and the line says what Sl. 6 alone allowed
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1000000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.RET_GRATUITY_AGGREGATE);
            assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("1250000")));
        });
    }

    @Test
    void evaluate_whenTheTermsAreSilentOnDearnessAllowance_leaveItOutOfTheTenMonthAverage() {
        // Given — each month 1,00,000 basic and 20,000 DA
        Map<ComponentCode, Money> monthly = Map.of(ComponentCodes.BASIC, Money.of("100000"),
                ComponentCodes.DEARNESS_ALLOWANCE, Money.of("20000"));
        List<RetirementPayment> gratuity = List.of(
                RetirementPayment.otherGratuity(ComponentCodes.GRATUITY, retiringInJune(2026, monthly)));
        List<Paid> paid = List.of(new Paid(ComponentCodes.GRATUITY, "1500000"));

        // When
        NodeResult silent = run(2026, FIRST_SCHEDULE_RATES, paid, gratuity, NOTHING_BEFORE, false);
        NodeResult providing = run(2026, FIRST_SCHEDULE_RATES, paid, gratuity, NOTHING_BEFORE, true);

        // Then — s.19(2)(b): 1/2 × 1,00,000 × 25 = 12,50,000; with DA 1/2 × 1,20,000 × 25 = 15,00,000
        assertThat(silent.value()).isEqualTo(MoneyValue.of(Money.of("1250000")));
        assertThat(providing.value()).isEqualTo(MoneyValue.of(Money.of("1500000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOtherLeaveEncashment_deductsTheLeastOfItsFourLimbs(int year) {
        // Given — leave at credit 9,00,000; average 1,00,000, so ten months 10,00,000; limit 25,00,000
        LeaveAtRetirement leave = new LeaveAtRetirement(LocalDate.of(year, 6, 30), Money.of("900000"),
                Money.of("100000"));

        // When — received 8,00,000, then 12,00,000
        NodeResult below = run(year, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.LEAVE_ENCASHMENT, "800000")),
                List.of(RetirementPayment.otherLeaveEncashment(ComponentCodes.LEAVE_ENCASHMENT, leave)));
        NodeResult above = run(year, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.LEAVE_ENCASHMENT, "1200000")),
                List.of(RetirementPayment.otherLeaveEncashment(ComponentCodes.LEAVE_ENCASHMENT, leave)));

        // Then
        assertThat(below.value()).isEqualTo(MoneyValue.of(Money.of("800000")));
        assertThat(above.value()).isEqualTo(MoneyValue.of(Money.of("900000")));
        // — and the limb that bound is named as each year's Act states it: TY 2025-26 values the leave at the average
        // salary (1961 s.10(10AA)(ii)); TY 2026-27 takes the cash equivalent of the leave salary (Sl. 14(a))
        assertThat(above.trace()).singleElement().satisfies(line -> assertThat(line.limitedBy()).contains(
                year == 2025 ? "leave at credit valued at the ten-month average salary" : "leave salary at credit"));
    }

    @Test
    void evaluate_whenEarlierYearsUsedMostOfTheLeaveLimit_deductsOnlyWhatRemains() {
        // When — s.19(2)(f): 25,00,000 − 20,00,000 = 5,00,000, below the 8,00,000 received
        NodeResult result = run(2026, NEW_TAX_REGIME, List.of(new Paid(ComponentCodes.LEAVE_ENCASHMENT, "800000")),
                List.of(RetirementPayment.otherLeaveEncashment(ComponentCodes.LEAVE_ENCASHMENT,
                        new LeaveAtRetirement(LocalDate.of(2026, 6, 30), Money.of("900000"), Money.of("100000")))),
                allowedBefore("0", "2000000"));

        // Then — and in the new regime too
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("500000")));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.RET_LEAVE_ENCASHMENT_AGGREGATE));
    }

    @Test
    void evaluate_whenTheGratuityEventPrecedesTheNotification_isRefused() {
        // Given — S.O. 1213(E) covers events on or after 29-3-2018
        LocalDate separatedOn = LocalDate.of(2018, 3, 1);
        List<MonthOfPay> months = new ArrayList<>();
        for (int back = 10; back >= 1; back--) {
            months.add(new MonthOfPay(java.time.YearMonth.from(separatedOn).minusMonths(back),
                    Map.of(ComponentCodes.BASIC, Money.of("100000"))));
        }
        Separation early = new Separation(LocalDate.of(1990, 4, 1), separatedOn, months);

        // When / Then
        assertThatThrownBy(() -> run(2025, FIRST_SCHEDULE_RATES,
                        List.of(new Paid(ComponentCodes.GRATUITY, "1500000")),
                        List.of(RetirementPayment.otherGratuity(ComponentCodes.GRATUITY, early))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2018-03-29");
    }

    @Test
    void evaluate_whenTheLeaveRetirementPrecedesTheNotification_isRefused() {
        // Given — S.O. 2276(E) is deemed in force from 1-4-2023
        LeaveAtRetirement early = new LeaveAtRetirement(LocalDate.of(2023, 3, 31), Money.of("900000"),
                Money.of("100000"));

        // When / Then
        assertThatThrownBy(() -> run(2025, FIRST_SCHEDULE_RATES,
                        List.of(new Paid(ComponentCodes.LEAVE_ENCASHMENT, "800000")),
                        List.of(RetirementPayment.otherLeaveEncashment(ComponentCodes.LEAVE_ENCASHMENT, early))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2023-04-01");
    }

    @Test
    void evaluate_whenGratuityAndLeaveNameDifferentSeparations_isRefused() {
        // When / Then — one separation, one day
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES,
                        List.of(new Paid(ComponentCodes.GRATUITY, "1500000"),
                                new Paid(ComponentCodes.LEAVE_ENCASHMENT, "800000")),
                        List.of(RetirementPayment.otherGratuity(ComponentCodes.GRATUITY,
                                        retiringInJune(2026, Map.of(ComponentCodes.BASIC, Money.of("100000")))),
                                RetirementPayment.otherLeaveEncashment(ComponentCodes.LEAVE_ENCASHMENT,
                                        new LeaveAtRetirement(LocalDate.of(2026, 5, 31), Money.of("900000"),
                                                Money.of("100000"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-06-30")
                .hasMessageContaining("2026-05-31");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void rows_whenAnEntrysLimitAndItsAggregatesAreRead_statesOneNotifiedAmount(int year) {
        // Given — one notification each, stated twice: Sl. 6(b) and s.19(2)(a)'s A; Sl. 14(c) and s.19(2)(f)'s A
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        TemporalContext t = TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21));
        CapContext nothing = new CapContext(Map.of());

        // When
        Money gratuityLimit = limb(pack, RuleIds.RET_GRATUITY_OTHER, "notified limit", t);
        Money leaveLimit = limb(pack, RuleIds.RET_LEAVE_ENCASHMENT_OTHER, "notified limit", t);

        // Then — S.O. 1213(E) twenty lakh rupees; S.O. 2276(E) Rs. 25,00,000
        assertThat(gratuityLimit).isEqualTo(Money.of("2000000"))
                .isEqualTo(TemporallyApportionedCap.apply(pack.rule(RuleIds.RET_GRATUITY_AGGREGATE), t, nothing).value());
        assertThat(leaveLimit).isEqualTo(Money.of("2500000")).isEqualTo(
                TemporallyApportionedCap.apply(pack.rule(RuleIds.RET_LEAVE_ENCASHMENT_AGGREGATE), t, nothing).value());
    }

    private static Money limb(RulePack pack, com.thiru.wealthlens.taxengine.rules.id.RuleId rule, String name,
            TemporalContext t) {
        LeastOf leastOf = (LeastOf) pack.rule(rule).versionAt(t).cap();
        return leastOf.limbs().stream().filter(limb -> limb.name().equals(name)).findFirst().orElseThrow()
                .shape().apply(new CapContext(Map.of())).value();
    }

    // ---------------------------------------------------------------- statutory gratuity and retrenchment (D-109)

    private static RetirementPayment statutory(ComponentCode component, RetirementEntry entry, int year,
            String amount) {
        return RetirementPayment.statutory(component, entry,
                new StatutoryEntitlement(LocalDate.of(year, 12, 15), Money.of(amount)));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenStatutoryGratuity_deductsUpToTheAmountTheStatuteEntitles(int year) {
        // When — the employer's figure 11,53,846.15 (15/26 × 1,00,000 × 20); received 15,00,000, then 9,00,000
        NodeResult above = run(year, FIRST_SCHEDULE_RATES, List.of(new Paid(ComponentCodes.GRATUITY, "1500000")),
                List.of(statutory(ComponentCodes.GRATUITY, RetirementEntry.STATUTORY_GRATUITY, year, "1153846.15")));
        NodeResult below = run(year, FIRST_SCHEDULE_RATES, List.of(new Paid(ComponentCodes.GRATUITY, "900000")),
                List.of(statutory(ComponentCodes.GRATUITY, RetirementEntry.STATUTORY_GRATUITY, year, "1153846.15")));

        // Then — the line names the separation day, which says which Act the figure answers to (D-107), and is
        // unverified while the law names a repealed Act
        assertThat(above.value()).isEqualTo(MoneyValue.of(Money.of("1153846.15")));
        assertThat(below.value()).isEqualTo(MoneyValue.of(Money.of("900000")));
        assertThat(above.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.RET_GRATUITY_STATUTORY);
            assertThat(line.limitedBy()).hasValueSatisfying(why -> assertThat(why)
                    .contains("amount under the gratuity statute").contains(year + "-12-15"));
            assertThat(line.provenance().verificationLevel()).isEqualTo(VerificationLevel.UNVERIFIED);
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenRetrenchmentAboveTheNotifiedLimit_deductsTheLimit(int year) {
        // When — least of 8,00,000 received, the employer's 6,00,000 and 5,00,000
        NodeResult result = run(year, FIRST_SCHEDULE_RATES,
                List.of(new Paid(ComponentCodes.RETRENCHMENT_COMPENSATION, "800000")),
                List.of(statutory(ComponentCodes.RETRENCHMENT_COMPENSATION, RetirementEntry.RETRENCHMENT, year,
                        "600000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("500000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.RET_RETRENCHMENT);
            assertThat(line.limitedBy()).hasValueSatisfying(why -> assertThat(why).contains("notified limit"));
        });
    }

    @Test
    void evaluate_whenTheStatutoryRetrenchmentAmountIsLeast_deductsItInTheNewRegime() {
        // When — least of 8,00,000, 3,00,000 and 5,00,000; s.202(2)(a) excludes no retirement entry
        NodeResult result = run(2026, NEW_TAX_REGIME,
                List.of(new Paid(ComponentCodes.RETRENCHMENT_COMPENSATION, "800000")),
                List.of(statutory(ComponentCodes.RETRENCHMENT_COMPENSATION, RetirementEntry.RETRENCHMENT, 2026,
                        "300000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("300000")));
    }
}
