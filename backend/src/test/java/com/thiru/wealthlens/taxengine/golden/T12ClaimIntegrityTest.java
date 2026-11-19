package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.DonationClause;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.HousingLoan;
import com.thiru.wealthlens.taxengine.input.LoanPurpose;
import com.thiru.wealthlens.taxengine.input.LoanUse;
import com.thiru.wealthlens.taxengine.input.PurposeLoanInterest;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@code DR 12} C8's engine rows, T12-5 to T12-10, each a whole computation through the shipped pipeline (W-119d C1a):
 * the level F24 is stated at, beside the node tests that prove the same facts one node at a time. TY 2026-27, the old
 * regime, basic pay 10,00,000. T12-1 to T12-4, T12-13 and T12-14 are the panel's ({@code T12ClaimIntegrityPanelTest},
 * {@code ForbiddenWordingTest}); T12-2, T12-11 and T12-12 are not run (errata under C8).
 */
class T12ClaimIntegrityTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final Rate WHOLE = Rate.percent("100");

    private static EnteredFacts salaried() {
        return EnteredFacts.none().withEmployments(List.of(new Employment(
                List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of("1000000"))), false, EmployerKind.OTHER, false,
                Optional.empty())));
    }

    private static PipelineRunner.Result run(EnteredFacts entered) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return RUNNER.run(new EngineInputs(TY_2026_27, OLD, plain.temporal(), entered, plain.assessee(), plain.family(),
                plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing())).result();
    }

    private static List<TraceLine> lines(PipelineRunner.Result run, RuleId rule) {
        return run.trace().stream().filter(line -> line.rule().equals(rule)).toList();
    }

    private static Money chapterEight(PipelineRunner.Result run) {
        return ((MoneyValue) run.valueOf(NodeIds.CHAPTERVIII)).amount();
    }

    private static HealthPayment ownHealth(HealthPaymentKind kind, String amount, boolean inCash) {
        return new HealthPayment(kind, true, List.of(), Money.of(amount), inCash, 1);
    }

    @Test
    void t12_05_whenAHealthPremiumIsPaidInCash_deductsNothingNamingTheCashRow() {
        // When — s.126(9)(b): any mode other than cash
        PipelineRunner.Result run = run(salaried().withHealthPayments(
                List.of(ownHealth(HealthPaymentKind.INSURANCE_PREMIUM, "10000", true))));

        // Then
        assertThat(chapterEight(run)).isEqualTo(Money.ZERO);
        assertThat(lines(run, RuleIds.DED_HEALTH_CASH_PERMITTED)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
            assertThat(line.limitedBy()).hasValueSatisfying(why ->
                    assertThat(why).contains(RuleIds.DED_HEALTH_CASH_PERMITTED.value()));
        });
    }

    @Test
    void t12_06_whenAPreventiveCheckUpIsPaidInCash_deductsIt() {
        // When — s.126(9)(a): the one payment cash may make; 3,000 within s.126(3)'s 5,000
        PipelineRunner.Result run = run(salaried().withHealthPayments(
                List.of(ownHealth(HealthPaymentKind.PREVENTIVE_CHECKUP, "3000", true))));

        // Then
        assertThat(chapterEight(run)).isEqualTo(Money.of("3000"));
        assertThat(lines(run, RuleIds.DED_HEALTH_CASH_PERMITTED))
                .noneMatch(line -> line.out().equals(MoneyValue.of(Money.ZERO)));
    }

    @Test
    void t12_07_whenADonationOfFiveThousandIsGivenInCash_deductsNothingCitingTheCashLimit() {
        // When — s.133(5): cash above 2,000 is not allowed
        PipelineRunner.Result run = run(withDonations(salaried(),
                List.of(new Donation(DonationClause.NAMED_FUND, Money.of("5000"), true, true))));

        // Then
        assertThat(chapterEight(run)).isEqualTo(Money.ZERO);
        assertThat(lines(run, RuleIds.DED_DONATION_CASH)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
            assertThat(line.reason()).hasValueSatisfying(why ->
                    assertThat(why.code()).isEqualTo(ReasonCodes.DONATION_CASH_LIMIT));
        });
    }

    @Test
    void t12_08_whenADonationOfFifteenHundredInCashIsNotReported_isAllowedOnModeButBarredUntilReported() {
        // When — s.133(5) allows 1,500 in cash; s.133(6) allows a charitable institution's donation only on its report
        PipelineRunner.Result run = run(withDonations(salaried(),
                List.of(new Donation(DonationClause.CHARITABLE_INSTITUTION, Money.of("1500"), true, false))));

        // Then — no cash bar, and the share's line nil naming the report bar
        assertThat(lines(run, RuleIds.DED_DONATION_CASH)).isEmpty();
        assertThat(lines(run, RuleIds.DED_DONATION_HALF)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
            assertThat(line.limitedBy()).hasValueSatisfying(why ->
                    assertThat(why).contains(RuleIds.DED_DONATION_UNREPORTED.value()));
            assertThat(line.reason()).hasValueSatisfying(why ->
                    assertThat(why.code()).isEqualTo(ReasonCodes.DONATION_UNREPORTED));
        });
        assertThat(chapterEight(run)).isEqualTo(Money.ZERO);
    }

    @Test
    void t12_09_whenTheSameInterestIsClaimedUnderHousePropertyAndSection131_isRefused() {
        // Given — 1,50,000 left out of house property as under s.131, the s.131 loan carrying 1,00,000
        HousingLoan split = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, LocalDate.of(2022, 5, 1),
                Money.of("320000"), Money.ZERO, Money.of("150000"), true, false);
        HouseProperty let = new HouseProperty(HouseOccupancy.LET, Money.of("480000"), Money.of("540000"), Money.ZERO,
                false, Money.of("18000"), WHOLE, Money.ZERO, Optional.of(LocalDate.of(2024, 6, 1)), List.of(split));
        PurposeLoanInterest section131 = new PurposeLoanInterest(LoanPurpose.RESIDENTIAL_HOUSE, Money.of("100000"),
                LocalDate.of(2019, 6, 1), Money.of("3000000"), Optional.empty(), Optional.of(Money.of("4000000")), false);
        EnteredFacts twice = replaced(salaried().withHouseProperties(List.of(let)), "purposeLoanInterest",
                List.of(section131));

        // When / Then
        assertThatThrownBy(() -> run(twice)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enter the same amount on both sides");
    }

    @Test
    void t12_10_whenNilAnnualValueIsClaimedOnAHouseLetForADay_isRefused() {
        // When / Then — S2 s.21(7)(b): a house let at any time is let; its day's rent entered against an own residence
        assertThatThrownBy(() -> new HouseProperty(HouseOccupancy.OWN_RESIDENCE, Money.of("300000"), Money.of("1000"),
                Money.ZERO, false, Money.ZERO, WHOLE, Money.ZERO, Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enter it as LET");
    }

    private static EnteredFacts withDonations(EnteredFacts entered, List<Donation> donations) {
        return replaced(entered, "donations", donations);
    }

    /** The facts with one list replaced; EnteredFacts offers a {@code with…} only for the lists the optimiser moves. */
    private static EnteredFacts replaced(EnteredFacts entered, String component, Object value) {
        try {
            var components = EnteredFacts.class.getRecordComponents();
            Object[] args = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                args[i] = components[i].getName().equals(component) ? value : components[i].getAccessor().invoke(entered);
            }
            return EnteredFacts.class.getDeclaredConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("EnteredFacts has no component " + component, e);
        }
    }
}
