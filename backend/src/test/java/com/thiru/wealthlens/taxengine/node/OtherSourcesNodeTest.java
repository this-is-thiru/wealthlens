package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.BusinessTrustUnits;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.GiftExclusion;
import com.thiru.wealthlens.taxengine.input.GiftKind;
import com.thiru.wealthlens.taxengine.input.GiftReceived;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.input.ProvidentFundAccount;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Income from other sources: interest and dividends (S2 ss.92–94; 1961 ss.56–58; D-194). Figures by hand from the
 * sources quoted in {@code docs/taxplanning-build/sources/2026-10-16-other-sources.md}: the 20% of the 1961 s.57
 * proviso (FA 2020 s.30(b)), nil from TY 2026-27 (FA 2026 s.44(b)), 50% of interest on compensation; and the
 * provident fund thresholds of {@code 2026-10-17-pf-own-interest.md} — ₹5,00,000 where the employer makes no
 * contribution, ₹2,50,000 otherwise (S2 Schedule II Sl. 3(a), 4(a); 1961 s.10(11), (12) provisos; D-198).
 */
class OtherSourcesNodeTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");

    private static OtherSourceItem item(OtherSourceKind kind, String received, String commission, String interest,
            String other) {
        return new OtherSourceItem(kind, Money.of(received), Money.of(commission), Money.of(interest), Money.of(other),
                false);
    }

    private static NodeResult run(int year, List<OtherSourceItem> items) {
        return run(year, OLD, items);
    }

    private static NodeResult run(int year, RegimeCode regime, List<OtherSourceItem> items) {
        return run(year, regime, items, List.of());
    }

    private static NodeResult run(int year, RegimeCode regime, List<OtherSourceItem> items,
            List<ProvidentFundAccount> accounts) {
        return run(year, regime, items, accounts, List.of());
    }

    private static NodeResult run(int year, RegimeCode regime, List<OtherSourceItem> items,
            List<ProvidentFundAccount> accounts, List<GiftReceived> gifts) {
        return run(year, regime, items, accounts, gifts, List.of());
    }

    private static NodeResult run(int year, RegimeCode regime, List<OtherSourceItem> items,
            List<ProvidentFundAccount> accounts, List<GiftReceived> gifts, List<BusinessTrustUnits> trusts) {
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
                        List.of(), List.of(), items, accounts, gifts, trusts),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs).run(new OtherSourcesNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenNothingIsEntered_isNil(int year) {
        // When
        NodeResult result = run(year, List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenInterestOnSecuritiesBearsCommissionAndExpenses_deductsBoth(int year) {
        // Given — 1,00,000 of interest on securities; 2,000 to the banker; 3,000 other expenditure
        OtherSourceItem securities = item(OtherSourceKind.INTEREST_ON_SECURITIES, "100000", "2000", "0", "3000");

        // When
        NodeResult result = run(year, List.of(securities));

        // Then — 1,00,000 − 2,000 − 3,000 (S2 s.93(1)(a), (e); 1961 s.57(i), (iii))
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("95000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.OS_INTEREST,
                RuleIds.OS_BANKER_COMMISSION, RuleIds.OS_OTHER_EXPENDITURE);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOtherInterestBearsABankersCommission_allowsItNot(int year) {
        // Given — savings interest of 40,000 with 500 entered as a banker's commission
        OtherSourceItem savings = item(OtherSourceKind.OTHER_INTEREST, "40000", "500", "0", "0");

        // When
        NodeResult result = run(year, List.of(savings));

        // Then — clause (a) / (i) reaches interest on securities only: a nil line, the whole 40,000 chargeable
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
        assertThat(result.trace().get(1).rule()).isEqualTo(RuleIds.OS_BANKER_COMMISSION);
        assertThat(result.trace().get(1).out()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenDividendInterestExceedsTwentyPerCentInTaxYear2025_allowsTwentyPerCentOfTheYearsDividends() {
        // Given — a dividend of 5,00,000 carrying 1,50,000 of interest, and unit income of 1,00,000
        List<OtherSourceItem> items = List.of(
                item(OtherSourceKind.DIVIDEND, "500000", "0", "150000", "0"),
                item(OtherSourceKind.UNIT_INCOME, "100000", "0", "0", "0"));

        // When
        NodeResult result = run(2025, items);

        // Then — the limit is the year's: 20% of 6,00,000 = 1,20,000 < 1,50,000; 6,00,000 − 1,20,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("480000")));
        assertThat(result.trace().getLast().rule()).isEqualTo(RuleIds.OS_DIVIDEND_INTEREST);
        assertThat(result.trace().getLast().out()).isEqualTo(MoneyValue.of(Money.of("120000")));
    }

    @Test
    void evaluate_whenDividendInterestIsWithinTwentyPerCentInTaxYear2025_allowsTheWholeInterest() {
        // Given — a dividend of 5,00,000 carrying 60,000 of interest
        List<OtherSourceItem> items = List.of(item(OtherSourceKind.DIVIDEND, "500000", "0", "60000", "0"));

        // When
        NodeResult result = run(2025, items);

        // Then — 60,000 < 1,00,000 (20% of 5,00,000); 5,00,000 − 60,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("440000")));
    }

    @Test
    void evaluate_whenDividendBearsInterestInTaxYear2026_allowsNothing() {
        // Given — the same dividend and interest as TY 2025-26's
        List<OtherSourceItem> items = List.of(item(OtherSourceKind.DIVIDEND, "500000", "0", "60000", "0"));

        // When
        NodeResult result = run(2026, items);

        // Then — S2 s.93(2) as substituted by FA 2026 s.44(b): no deduction
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("500000")));
        assertThat(result.trace().getLast().out()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenADividendBearsABankersCommission_allowsItNot(int year) {
        // Given — a dividend of 2,00,000 with 1,000 to the banker
        List<OtherSourceItem> items = List.of(item(OtherSourceKind.DIVIDEND, "200000", "1000", "0", "0"));

        // When
        NodeResult result = run(year, items);

        // Then — the 1961 s.57 proviso (FA 2020 s.30(b)) and S2 s.93(2): only interest expense, never commission
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("200000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.OS_DIVIDEND, RuleIds.OS_DIVIDEND_INTEREST);
        assertThat(result.trace().getLast().out()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenADeemedDividendBearsInterestInTaxYear2025_allowsNothingAndKeepsItOutOfTheLimit() {
        // Given — a deemed dividend of 2,00,000 with 50,000 of interest, beside a dividend of 1,00,000 with 30,000
        List<OtherSourceItem> items = List.of(
                item(OtherSourceKind.DEEMED_DIVIDEND, "200000", "0", "50000", "0"),
                item(OtherSourceKind.DIVIDEND, "100000", "0", "30000", "0"));

        // When
        NodeResult result = run(2025, items);

        // Then — nothing against the deemed one (F(No.2)A 2024 s.24(c)); the 20% is of 1,00,000 only = 20,000;
        // 2,00,000 + 1,00,000 − 20,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("280000")));
        assertThat(result.trace()).extracting(TraceLine::rule).contains(RuleIds.OS_DEEMED_DIVIDEND);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenInterestOnCompensationIsEntered_deductsHalfAndNothingElse(int year) {
        // Given — 80,000 of interest on enhanced compensation, with 5,000 entered as expenditure
        List<OtherSourceItem> items = List.of(
                item(OtherSourceKind.INTEREST_ON_COMPENSATION, "80000", "0", "0", "5000"));

        // When
        NodeResult result = run(year, items);

        // Then — 50% of 80,000 = 40,000 deducted; the 5,000 is a nil line (S2 s.93(1)(f); 1961 s.57(iv))
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
        assertThat(result.trace().getLast().out()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenAnExpenseIsPayableOutsideIndiaUntaxed_deductsNothingForIt() {
        // Given — interest on securities of 1,00,000 with 10,000 of expenditure payable abroad untaxed
        OtherSourceItem untaxed = new OtherSourceItem(OtherSourceKind.INTEREST_ON_SECURITIES, Money.of("100000"),
                Money.ZERO, Money.ZERO, Money.of("10000"), true);

        // When
        NodeResult result = run(2026, List.of(untaxed));

        // Then — S2 s.94(1)(b), (c)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
        assertThat(result.trace().getLast().limitedBy()).hasValueSatisfying(
                why -> assertThat(why).contains("payable outside India untaxed"));
    }

    @Test
    void evaluate_whenWinningsAreEntered_refusesThem() {
        // When / Then — their rate is deferred in the coverage register
        assertThatThrownBy(() -> run(2026, List.of(item(OtherSourceKind.WINNINGS, "100000", "0", "0", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s.115BB");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = OtherSourceKind.class, names = {"EMPLOYEE_CONTRIBUTIONS", "MACHINERY_LETTING",
        "MACHINERY_AND_BUILDING_LETTING"})
    void evaluate_whenAKindTakesTheBusinessHeadsDeductions_refusesItNamingThem(OtherSourceKind kind) {
        // When / Then — S2 s.93(1)(b), (c) send these to ss.28, 29(1)(e), 33, deferred with the business head (D-202)
        assertThatThrownBy(() -> run(2026, List.of(item(kind, "100000", "0", "0", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("business head")
                .hasMessageContaining("D-202");
    }

    @Test
    void evaluate_whenLifePolicySumsAreEntered_refusesThemNamingTheRowThatBuildsThem() {
        // When / Then
        assertThatThrownBy(() -> run(2026, List.of(item(OtherSourceKind.LIFE_POLICY, "100000", "0", "0", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W-079g2");
    }

    @Test
    void evaluate_whenExpensesExceedTheIncome_refusesTheLossUntilSetOff() {
        // When / Then — 10,000 of interest against 15,000 of expenditure
        assertThatThrownBy(() -> run(2026, List.of(item(OtherSourceKind.OTHER_INTEREST, "10000", "0", "0", "15000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W-081");
    }

    /** {@code DR 08} T6-6: savings 14,000; time deposit 90,000; dividend 2,00,000 bearing 60,000 of interest. */
    private static final List<OtherSourceItem> T6_6 = List.of(
            item(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST, "14000", "0", "0", "0"),
            item(OtherSourceKind.DEPOSIT_INTEREST, "90000", "0", "0", "0"),
            item(OtherSourceKind.DIVIDEND, "200000", "0", "60000", "0"));

    @Test
    void evaluate_whenT6Dash6IsEnteredInTaxYear2026_isThreeLakhFourThousand() {
        // When
        NodeResult result = run(2026, T6_6);

        // Then — DR 08 T6-6: "other-sources income = 14,000 + 90,000 + 2,00,000 = 3,04,000" (FA 2026 s.44(b))
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("304000")));
    }

    @Test
    void evaluate_whenT6Dash6IsEnteredInTaxYear2025_allowsTwentyPerCentOfTheDividend() {
        // When
        NodeResult result = run(2025, T6_6);

        // Then — DR 08 T6-6: "0.20 × 2,00,000 = 40,000"; "3,04,000 − 40,000 = 2,64,000"
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("264000")));
    }

    private static OtherSourceItem pension(String received) {
        return item(OtherSourceKind.FAMILY_PENSION, received, "0", "0", "0");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAFamilyPensionsThirdIsUnderTheOldLimit_deductsTheThird(int year) {
        // When — 30,000 − min(30,000 ÷ 3, 15,000) (S2 s.93(1)(d)(ii); 1961 s.57(iia))
        NodeResult result = run(year, OLD, List.of(pension("30000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("20000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.OS_FAMILY_PENSION);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAFamilyPensionsThirdExceedsTheOldLimit_deductsFifteenThousand(int year) {
        // When — 60,000 − min(20,000, 15,000)
        NodeResult result = run(year, OLD, List.of(pension("60000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("45000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAFamilyPensionsThirdIsUnderTheNewRegimesLimit_deductsTheThird(int year) {
        // When — 60,000 − min(20,000, 25,000) (S2 s.93(1)(d)(i); F(No.2)A 2024 s.24(b))
        NodeResult result = run(year, NEW, List.of(pension("60000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.OS_FAMILY_PENSION_NEW_TAX_REGIME);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAFamilyPensionsThirdExceedsTheNewRegimesLimit_deductsTwentyFiveThousand(int year) {
        // When — 90,000 − min(30,000, 25,000)
        NodeResult result = run(year, NEW, List.of(pension("90000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("65000")));
    }

    @Test
    void evaluate_whenAFamilyPensionsThirdIsNotWhole_dividesOnceToPaise() {
        // When — 10,000 − 3,333.33 (10,000 ÷ 3 = 3,333.333…)
        NodeResult result = run(2026, OLD, List.of(pension("10000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("6666.67")));
    }

    @Test
    void evaluate_whenAFamilyPensionBearsOtherExpenditure_deductsItAfterTheThird() {
        // Given — 60,000 of family pension, 1,000 of other expenditure
        OtherSourceItem expensed = item(OtherSourceKind.FAMILY_PENSION, "60000", "0", "0", "1000");

        // When — 60,000 − 15,000 − 1,000 (S2 s.93(1)(d)(ii), (e))
        NodeResult result = run(2026, OLD, List.of(expensed));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("44000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.OS_FAMILY_PENSION, RuleIds.OS_OTHER_EXPENDITURE);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAFundPensionIsCommuted_deductsTheEntireAmount(int year) {
        // When — S2 s.93(1)(g); 1961 s.10(10A)(iii)
        NodeResult result = run(year, OLD, List.of(item(OtherSourceKind.COMMUTED_FUND_PENSION, "500000", "0", "0",
                "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.OS_COMMUTED_PENSION);
            assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("500000")));
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAGovernmentGratuityIsReceivedOnDeath_deductsTheEntireAmount(int year) {
        // When — S2 s.93(1)(h) with s.19(2)(g); 1961 s.10(10)(i)
        NodeResult result = run(year, NEW, List.of(item(OtherSourceKind.DEATH_GRATUITY, "1000000", "0", "0", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.OS_DEATH_GRATUITY);
    }

    @Test
    void evaluate_whenAnExpenseIsEnteredAgainstADeathGratuity_isANilLine() {
        // When — the entire amount is deducted already, so the 2,000 has nothing to bear against
        NodeResult result = run(2026, OLD, List.of(item(OtherSourceKind.DEATH_GRATUITY, "1000000", "0", "0", "2000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::out)
                .containsExactly(MoneyValue.of(Money.ZERO), MoneyValue.of(Money.ZERO));
        assertThat(result.trace().get(1).limitedBy().orElseThrow()).contains("entire amount");
    }

    private static ProvidentFundAccount fund(String contributed, boolean employerContributes, String interest) {
        return new ProvidentFundAccount(Money.of(contributed), employerContributes, Money.of(interest));
    }

    private static NodeResult runFunds(int year, ProvidentFundAccount... accounts) {
        return run(year, OLD, List.of(), List.of(accounts));
    }

    /** The MEASURE lines' out: each fund's contribution beyond its threshold, in order. */
    private static List<NodeValue> excesses(NodeResult result) {
        return result.trace().stream().filter(line -> line.kind() == StepKind.MEASURE).map(TraceLine::out).toList();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheEmployerContributesAndTheContributionExceedsTheThreshold_measuresTheExcessAndTaxesTheInterest(
            int year) {
        // Given — DR 08 T4-4's 6,00,000 with an employer that contributes; the fund reports 12,000 taxable interest
        ProvidentFundAccount account = fund("600000", true, "12000");

        // When
        NodeResult result = runFunds(year, account);

        // Then — 6,00,000 − 2,50,000 = 3,50,000 enters the taxable account; the 12,000 is income
        assertThat(excesses(result)).containsExactly(MoneyValue.of(Money.of("350000")));
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("12000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsOnly(RuleIds.OS_PF_TAXABLE_INTEREST);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenNoEmployerContributes_measuresTheExcessOverTheHigherThreshold(int year) {
        // When — 6,00,000 to a fund the employer does not contribute to; 5,000 taxable interest
        NodeResult result = runFunds(year, fund("600000", false, "5000"));

        // Then — 6,00,000 − 5,00,000 = 1,00,000
        assertThat(excesses(result)).containsExactly(MoneyValue.of(Money.of("100000")));
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("5000")));
    }

    @Test
    void evaluate_whenTheContributionEqualsTheThreshold_measuresNoExcess() {
        // When — exactly 5,00,000, no employer contribution: "exceeds", so equal is not
        NodeResult result = runFunds(2026, fund("500000", false, "0"));

        // Then
        assertThat(excesses(result)).containsExactly(MoneyValue.of(Money.ZERO));
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenTheContributionIsJustOverTheLowerThreshold_measuresTheSmallExcess() {
        // When — 2,60,000 with an employer that contributes; 400 taxable interest
        NodeResult result = runFunds(2026, fund("260000", true, "400"));

        // Then — 2,60,000 − 2,50,000 = 10,000
        assertThat(excesses(result)).containsExactly(MoneyValue.of(Money.of("10000")));
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("400")));
    }

    @Test
    void evaluate_whenThisYearIsUnderTheThresholdButEarlierYearsWereNot_taxesTheReportedInterest() {
        // When — 1,00,000 this year, but 8,000 accrued on earlier years' taxable contributions (r.277(3)(b))
        NodeResult result = runFunds(2026, fund("100000", true, "8000"));

        // Then
        assertThat(excesses(result)).containsExactly(MoneyValue.of(Money.ZERO));
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("8000")));
    }

    @Test
    void evaluate_whenTwoFundsAreEachUnderTheirThreshold_testsEachFundAlone() {
        // When — 4,00,000 (no employer) and 2,00,000 (employer): 6,00,000 together, under the threshold in each fund
        NodeResult result = runFunds(2026, fund("400000", false, "0"), fund("200000", true, "0"));

        // Then — "in that fund": nothing beyond either threshold
        assertThat(excesses(result)).containsExactly(MoneyValue.of(Money.ZERO), MoneyValue.of(Money.ZERO));
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenAFundSitsBesideAFamilyPension_addsBoth() {
        // When — family pension 30,000 in the old regime, and T4-4's fund with 12,000 taxable interest
        NodeResult result = run(2026, OLD, List.of(pension("30000")), List.of(fund("600000", true, "12000")));

        // Then — 30,000 − min(10,000, 15,000) = 20,000, plus 12,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("32000")));
    }

    private static GiftReceived gift(GiftKind kind, String consideration, String value) {
        return new GiftReceived(kind, Money.of(consideration), Money.of(value), Optional.empty());
    }

    private static GiftReceived sum(String value) {
        return gift(GiftKind.SUM_OF_MONEY, "0", value);
    }

    private static GiftReceived excluded(GiftExclusion ground, String value) {
        return new GiftReceived(GiftKind.SUM_OF_MONEY, Money.ZERO, Money.of(value), Optional.of(ground));
    }

    private static NodeResult runGifts(int year, GiftReceived... gifts) {
        return run(year, OLD, List.of(), List.of(), List.of(gifts));
    }

    /*
     * Gifts (S2 s.92(2)(m), (3); 1961 s.56(2)(x); D-201): ₹50,000, and for immovable property the higher of ₹50,000
     * and 10% of the consideration, from docs/taxplanning-build/sources/2026-10-17-gifts.md. Worked by hand.
     */

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheYearsSumsExceedTheThreshold_chargesTheWhole(int year) {
        // When — 30,000 and 25,000 from friends
        NodeResult result = runGifts(year, sum("30000"), sum("25000"));

        // Then — "the total of which exceeds ₹ 50000, the whole of such sum": 55,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("55000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsOnly(RuleIds.OS_GIFT);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheYearsSumsEqualTheThreshold_chargesNothing(int year) {
        // When — 30,000 and 20,000: 50,000 does not exceed 50,000
        NodeResult result = runGifts(year, sum("30000"), sum("20000"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenALargeSumIsFromARelative_leavesItOutOfTheTotal(int year) {
        // When — 1,00,000 from a relative and 40,000 from a friend
        NodeResult result = runGifts(year, excluded(GiftExclusion.RELATIVE, "100000"), sum("40000"));

        // Then — the relative's sum counts toward nothing; 40,000 alone does not exceed
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace().getFirst().kind()).isEqualTo(StepKind.EXEMPTION);
        assertThat(result.trace().getFirst().out()).isEqualTo(MoneyValue.of(Money.of("100000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenImmovablePropertyIsReceivedFree_chargesItsStampDutyValue(int year) {
        // When — a flat received for nothing, stamp duty value 6,00,000
        NodeResult result = runGifts(year, gift(GiftKind.IMMOVABLE_PROPERTY, "0", "600000"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("600000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenImmovablePropertyIsBoughtWithinTheTolerance_chargesNothing(int year) {
        // When — paid 50,00,000 for a stamp duty value of 54,00,000
        NodeResult result = runGifts(year, gift(GiftKind.IMMOVABLE_PROPERTY, "5000000", "5400000"));

        // Then — 4,00,000 is not more than max(50,000, 10% of 50,00,000 = 5,00,000)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenImmovablePropertyIsBoughtBeyondTheTolerance_chargesTheExcess(int year) {
        // When — paid 50,00,000 for 56,00,000; and paid 3,00,000 for 3,60,000
        NodeResult result = runGifts(year, gift(GiftKind.IMMOVABLE_PROPERTY, "5000000", "5600000"),
                gift(GiftKind.IMMOVABLE_PROPERTY, "300000", "360000"));

        // Then — 6,00,000 > 5,00,000; 60,000 > max(50,000, 30,000): each on its own, 6,60,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("660000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOtherPropertyIsReceivedFree_testsTheAggregate(int year) {
        // When — shares worth 30,000 and jewellery worth 25,000, both for nothing
        NodeResult result = runGifts(year, gift(GiftKind.OTHER_PROPERTY, "0", "30000"),
                gift(GiftKind.OTHER_PROPERTY, "0", "25000"));

        // Then — "the aggregate fair market value of which exceeds ₹ 50000": 55,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("55000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOtherPropertyIsBoughtBelowItsValue_chargesAShortfallAboveTheThreshold(int year) {
        // When — jewellery worth 1,60,000 for 1,00,000; another worth 1,50,000 for 1,00,000
        NodeResult result = runGifts(year, gift(GiftKind.OTHER_PROPERTY, "100000", "160000"),
                gift(GiftKind.OTHER_PROPERTY, "100000", "150000"));

        // Then — 60,000 exceeds 50,000; 50,000 does not: 60,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheSumsAreMarriageGifts_chargesNothing(int year) {
        // When
        NodeResult result = runGifts(year, excluded(GiftExclusion.MARRIAGE, "500000"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenTaxYear2025HasCovidDeathSumsAboveTheLimit_chargesTheRest() {
        // When — 12,00,000 to the family of a person who died of COVID-19, from others (1961 proviso (XIII)(B))
        NodeResult result = runGifts(2025, excluded(GiftExclusion.COVID_DEATH_FROM_OTHERS, "1200000"));

        // Then — 10,00,000 excluded; 2,00,000 remains and exceeds 50,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("200000")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.OS_GIFT_COVID_DEATH);
        assertThat(result.trace().getFirst().out()).isEqualTo(MoneyValue.of(Money.of("1000000")));
    }

    @Test
    void evaluate_whenTaxYear2026IsGivenACovidGround_isRefused() {
        // When / Then — the 2025 Act's s.92(3) has no such ground
        assertThatThrownBy(() -> runGifts(2026, excluded(GiftExclusion.COVID_TREATMENT, "100000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s.92(3) has no such exclusion");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenGiftsSitBesideInterest_addsBoth(int year) {
        // When — the 55,000 of sums and 10,000 of interest on securities
        NodeResult result = run(year, OLD, List.of(item(OtherSourceKind.INTEREST_ON_SECURITIES, "10000", "0", "0",
                "0")), List.of(), List.of(sum("30000"), sum("25000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("65000")));
    }

    private static NodeResult runTrusts(int year, BusinessTrustUnits... trusts) {
        return run(year, OLD, List.of(), List.of(), List.of(), List.of(trusts));
    }

    /*
     * The clauses received in full (S2 s.92(1), (2)(d), (h), (j), (k); 1961 s.56(1), (2)(iv), (ix), (xi), (xii); D-203),
     * from docs/taxplanning-build/sources/2026-10-17-other-sources-rest.md. Worked by hand.
     */

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenKeymanSumsBearExpenditure_chargesTheRest(int year) {
        // When — 5,00,000 under a Keyman policy, 10,000 laid out for it
        NodeResult result = run(year, List.of(item(OtherSourceKind.KEYMAN_POLICY, "500000", "0", "0", "10000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("490000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.OS_KEYMAN_POLICY, RuleIds.OS_OTHER_EXPENDITURE);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAnAdvanceIsForfeited_chargesItInFull(int year) {
        // When
        NodeResult result = run(year, List.of(item(OtherSourceKind.FORFEITED_ADVANCE, "200000", "0", "0", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("200000")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.OS_FORFEITED_ADVANCE);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenCompensationOnTerminationComesFromAThirdParty_chargesItInFull(int year) {
        // When
        NodeResult result = run(year, List.of(item(OtherSourceKind.TERMINATION_COMPENSATION, "300000", "0", "0",
                "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("300000")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.OS_TERMINATION_COMPENSATION);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenABusinessTrustDistributedBeyondIssueAndEarlierCharges_chargesTheSpecifiedSum(int year) {
        // When — A 1,50,000, B 1,00,000, C 20,000
        NodeResult result = runTrusts(year, new BusinessTrustUnits(Money.of("150000"), Money.of("100000"),
                Money.of("20000")));

        // Then — A − B − C = 30,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.OS_BUSINESS_TRUST);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenIssueAndEarlierChargesReachTheDistributions_chargesNothing(int year) {
        // When — B + C = 1,10,000 against A of 1,00,000, and against A of 1,10,000
        NodeResult result = runTrusts(year,
                new BusinessTrustUnits(Money.of("100000"), Money.of("80000"), Money.of("30000")),
                new BusinessTrustUnits(Money.of("110000"), Money.of("80000"), Money.of("30000")));

        // Then — "deemed to be zero, if the sum of B and C is greater than A"; equal gives zero too
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenResidualIncomeBearsExpenditure_chargesTheRest(int year) {
        // When — 25,000 of other income under the head, 5,000 laid out for it
        NodeResult result = run(year, List.of(item(OtherSourceKind.OTHER, "25000", "0", "0", "5000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("20000")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.OS_RESIDUAL);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenKeymanSumsCarryABankersCommission_allowsItNot(int year) {
        // When
        NodeResult result = run(year, List.of(item(OtherSourceKind.KEYMAN_POLICY, "500000", "1000", "0", "0")));

        // Then — a nil line; 5,00,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("500000")));
        assertThat(result.trace().get(1).rule()).isEqualTo(RuleIds.OS_BANKER_COMMISSION);
        assertThat(result.trace().get(1).out()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAForfeitedAdvanceSitsBesideInterest_addsBoth(int year) {
        // When
        NodeResult result = run(year, List.of(item(OtherSourceKind.FORFEITED_ADVANCE, "200000", "0", "0", "0"),
                item(OtherSourceKind.INTEREST_ON_SECURITIES, "10000", "0", "0", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("210000")));
    }
}
