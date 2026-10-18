package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.DonationClause;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Huf;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * s.153 through {@link ChapterEightNode}, both years (S2 s.153, s.2(100); 1961 ss.80TTA, 80TTB; D-195). Figures by
 * hand from {@code docs/taxplanning-build/sources/2026-10-16-deposit-interest.md}: ₹10,000 on savings-account
 * interest for anyone but a senior citizen, ₹50,000 on any deposit's interest for a senior citizen — resident and
 * sixty at any time in the year. Gross total income is 10,00,000 throughout, so s.122(2) never binds.
 */
class DepositInterestDeductionTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");

    private static Individual born(LocalDate dateOfBirth, ResidentialStatus residence) {
        return new Individual(dateOfBirth, residence, Optional.empty(), Optional.empty());
    }

    private static final Individual UNDER_SIXTY = born(LocalDate.of(1980, 1, 1), ResidentialStatus.RESIDENT);
    private static final Individual SIXTY_SEVEN = born(LocalDate.of(1959, 6, 1), ResidentialStatus.RESIDENT);

    private static OtherSourceItem savings(String interest) {
        return interest(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST, interest);
    }

    private static OtherSourceItem deposit(String interest) {
        return interest(OtherSourceKind.DEPOSIT_INTEREST, interest);
    }

    private static OtherSourceItem interest(OtherSourceKind kind, String interest) {
        return new OtherSourceItem(kind, Money.of(interest), Money.ZERO, Money.ZERO, Money.ZERO, false);
    }

    private static NodeResult run(int year, RegimeCode regime, Assessee assessee, List<OtherSourceItem> items) {
        return run(year, regime, assessee, items, List.of());
    }

    private static NodeResult run(int year, RegimeCode regime, Assessee assessee, List<OtherSourceItem> items,
            List<Donation> donations) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                        donations, List.of(), items, List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                assessee, plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs)
                .given(NodeIds.GTI, Money.of("1000000"))
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of()))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
                .run(new ChapterEightNode());
    }

    private static List<TraceLine> lines(NodeResult result, RuleId rule) {
        return result.trace().stream().filter(line -> line.rule().equals(rule)).toList();
    }

    private static MoneyValue rupees(String amount) {
        return MoneyValue.of(Money.of(amount));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenT6Dash6IsEnteredUnderSixty_deductsTenThousandOfTheSavingsInterest(int year) {
        // When — DR 08 T6-6: "DED-80TTA-001: savings-account interest only, cap 10,000; min(14,000, 10,000) = 10,000"
        NodeResult result = run(year, OLD, UNDER_SIXTY, List.of(savings("14000"), deposit("90000")));

        // Then — the time deposit's 90,000 is a nil line, never silently left out (s.153(2)(a); 1961 s.80TTA(1))
        assertThat(result.value()).isEqualTo(rupees("10000"));
        assertThat(lines(result, RuleIds.DED_SAVINGS_INTEREST)).extracting(TraceLine::in, TraceLine::out)
                .containsExactly(tuple(rupees("14000"), rupees("10000")), tuple(rupees("90000"), rupees("0")));
        assertThat(lines(result, RuleIds.DED_SAVINGS_INTEREST).get(1).limitedBy().orElseThrow())
                .contains("savings-account interest only");
        assertThat(lines(result, RuleIds.DED_DEPOSIT_INTEREST_SENIOR)).isEmpty();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenT6Dash7IsEnteredAtSixtySeven_deductsFiftyThousandOfAllTheDepositInterest(int year) {
        // When — DR 08 T6-7: "min(14,000 + 90,000 , 50,000) = 50,000"
        NodeResult result = run(year, OLD, SIXTY_SEVEN, List.of(savings("14000"), deposit("90000")));

        // Then — "DED-80TTA-001 does not apply to a senior citizen" (s.153(1)(a), (b); 1961 s.80TTA(1)'s brackets)
        assertThat(result.value()).isEqualTo(rupees("50000"));
        assertThat(lines(result, RuleIds.DED_DEPOSIT_INTEREST_SENIOR)).singleElement().satisfies(line -> {
            assertThat(line.in()).isEqualTo(rupees("104000"));
            assertThat(line.out()).isEqualTo(rupees("50000"));
        });
        assertThat(lines(result, RuleIds.DED_SAVINGS_INTEREST)).isEmpty();
    }

    @Test
    void evaluate_whenSavingsInterestIsUnderTheLimit_deductsTheWhole() {
        // When — "the whole of the interest up to a maximum amount of ₹ 10000": 6,000 is under it
        NodeResult result = run(2026, OLD, UNDER_SIXTY, List.of(savings("6000")));

        // Then
        assertThat(result.value()).isEqualTo(rupees("6000"));
    }

    @Test
    void evaluate_whenAHinduUndividedFamilyHasSavingsInterest_deductsTenThousand() {
        // When — s.153(1)(c) with (2)(a): min(14,000, 10,000)
        NodeResult result = run(2026, OLD, new Huf(ResidentialStatus.RESIDENT), List.of(savings("14000")));

        // Then
        assertThat(result.value()).isEqualTo(rupees("10000"));
        assertThat(lines(result, RuleIds.DED_SAVINGS_INTEREST)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("10000")));
    }

    @Test
    void evaluate_whenSixtyFiveButNonResident_isNotASeniorCitizenAndTakesTheSavingsLimit() {
        // Given — s.2(100): a senior citizen is "an individual resident in India"; s.153(1)(a) has no residence bar
        Individual nonResident = born(LocalDate.of(1961, 6, 1), ResidentialStatus.NON_RESIDENT);

        // When
        NodeResult result = run(2026, OLD, nonResident, List.of(savings("14000"), deposit("20000")));

        // Then — min(14,000, 10,000); the 20,000 a nil line
        assertThat(result.value()).isEqualTo(rupees("10000"));
        assertThat(lines(result, RuleIds.DED_DEPOSIT_INTEREST_SENIOR)).isEmpty();
    }

    @Test
    void evaluate_whenSixtyIsReachedOnTheYearsLastDay_isASeniorCitizen() {
        // Given — born 31-3-1967: sixty on 31-3-2027, "at any time during the relevant tax year" (s.2(100))
        Individual sixtyAtTheEnd = born(LocalDate.of(1967, 3, 31), ResidentialStatus.RESIDENT);

        // When
        NodeResult result = run(2026, OLD, sixtyAtTheEnd, List.of(deposit("30000")));

        // Then — 30,000, under 50,000
        assertThat(result.value()).isEqualTo(rupees("30000"));
        assertThat(lines(result, RuleIds.DED_DEPOSIT_INTEREST_SENIOR)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("30000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheNewRegimeApplies_isANilLineNamingTheRegime(int year) {
        // When — S2 s.202(2)(a)(xii); 1961 s.115BAC(2)(i)
        NodeResult result = run(year, NEW, UNDER_SIXTY, List.of(savings("14000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(lines(result, RuleIds.DED_SAVINGS_INTEREST)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(rupees("0"));
            assertThat(line.limitedBy().orElseThrow()).contains("NEW_TAX_REGIME");
        });
    }

    private static OtherSourceItem expensed(OtherSourceKind kind, String interest, String expenditure,
            boolean payableOutsideIndiaUntaxed) {
        return new OtherSourceItem(kind, Money.of(interest), Money.ZERO, Money.ZERO, Money.of(expenditure),
                payableOutsideIndiaUntaxed);
    }

    private static Money deducted(NodeResult result, RuleId row) {
        return lines(result, row).stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
    }

    @Test
    void evaluate_whenSavingsInterestBearsExpenditure_deductsTheNetInterest() {
        // When — 25,000 of savings interest less 18,000 of other expenditure = 7,000, under 10,000 (D-228)
        NodeResult result = run(2026, OLD, UNDER_SIXTY,
                List.of(expensed(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST, "25000", "18000", false)));

        // Then
        assertThat(lines(result, RuleIds.DED_SAVINGS_INTEREST)).singleElement().satisfies(line -> {
            assertThat(line.in()).isEqualTo(rupees("7000"));
            assertThat(line.out()).isEqualTo(rupees("7000"));
            assertThat(line.limitedBy().orElseThrow()).contains("net of the expenditure").contains("D-228");
        });
    }

    @Test
    void evaluate_whenTheNetSavingsInterestExceedsTheLimit_deductsTheLimit() {
        // When — 30,000 − 5,000 = 25,000, above 10,000
        NodeResult result = run(2026, OLD, UNDER_SIXTY,
                List.of(expensed(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST, "30000", "5000", false)));

        // Then
        assertThat(deducted(result, RuleIds.DED_SAVINGS_INTEREST)).isEqualTo(Money.of("10000"));
    }

    @Test
    void evaluate_whenASeniorsDepositInterestBearsExpenditure_deductsTheNetOfAllDeposits() {
        // When — deposit 80,000 − 40,000 + savings 6,000 = 46,000, under 50,000
        NodeResult result = run(2026, OLD, SIXTY_SEVEN,
                List.of(expensed(OtherSourceKind.DEPOSIT_INTEREST, "80000", "40000", false), savings("6000")));

        // Then
        assertThat(deducted(result, RuleIds.DED_DEPOSIT_INTEREST_SENIOR)).isEqualTo(Money.of("46000"));
    }

    @Test
    void evaluate_whenTheExpenditureIsPayableOutsideIndiaUntaxed_deductsOnTheInterestReceived() {
        // When — the head allows none of it (S2 s.94(1)(b), (c)), so the net is the 14,000 received; limit 10,000
        NodeResult result = run(2026, OLD, UNDER_SIXTY,
                List.of(expensed(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST, "14000", "1000", true)));

        // Then
        assertThat(deducted(result, RuleIds.DED_SAVINGS_INTEREST)).isEqualTo(Money.of("10000"));
    }

    @Test
    void evaluate_whenOneItemsExpenditureExceedsItsInterest_takesTheItemsTogether() {
        // When — 5,000 − 8,000 = −3,000 and 12,000: 9,000 together, under 10,000
        NodeResult result = run(2026, OLD, UNDER_SIXTY,
                List.of(expensed(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST, "5000", "8000", false), savings("12000")));

        // Then
        assertThat(deducted(result, RuleIds.DED_SAVINGS_INTEREST)).isEqualTo(Money.of("9000"));
    }

    @Test
    void evaluate_whenAnExpenseIsEnteredInTheNewRegime_isNotRefused() {
        // Given — the new regime allows no s.153 deduction, so the open question does not arise
        OtherSourceItem expensed = new OtherSourceItem(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST, Money.of("14000"),
                Money.ZERO, Money.ZERO, Money.of("1000"), false);

        // When
        NodeResult result = run(2026, NEW, UNDER_SIXTY, List.of(expensed));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenADonationsLimitBinds_worksItOnGrossTotalIncomeLessTheDepositInterest() {
        // Given — s.153 10,000 first; adjusted GTI 10,00,000 − 10,000 = 9,90,000, limit 10% = 99,000 (s.133(2))
        Donation charitable = new Donation(DonationClause.CHARITABLE_INSTITUTION, Money.of("240000"), false, true);

        // When — 2,40,000 counts up to 99,000; half is 49,500
        NodeResult result = run(2026, OLD, UNDER_SIXTY, List.of(savings("14000")), List.of(charitable));

        // Then — 10,000 + 49,500
        assertThat(lines(result, RuleIds.DED_DONATION_QUALIFYING)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("99000")));
        assertThat(result.value()).isEqualTo(rupees("59500"));
    }
}
