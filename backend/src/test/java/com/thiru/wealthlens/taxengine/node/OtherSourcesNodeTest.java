package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.input.BusinessTrustUnits;
import com.thiru.wealthlens.taxengine.input.ClaimedPolicy;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.GiftExclusion;
import com.thiru.wealthlens.taxengine.input.GiftKind;
import com.thiru.wealthlens.taxengine.input.GiftReceived;
import com.thiru.wealthlens.taxengine.input.LifePolicyReceipt;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.input.ProvidentFundAccount;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
        return run(year, regime, items, accounts, gifts, trusts, List.of());
    }

    private static NodeResult run(int year, RegimeCode regime, List<OtherSourceItem> items,
            List<ProvidentFundAccount> accounts, List<GiftReceived> gifts, List<BusinessTrustUnits> trusts,
            List<LifePolicyReceipt> policies) {
        return run(year, regime, new HeadEntries(items, accounts, gifts, trusts, policies, List.of()));
    }

    /** Everything entered for the head, in {@link EnteredFacts}' order. */
    private record HeadEntries(List<OtherSourceItem> items, List<ProvidentFundAccount> accounts,
            List<GiftReceived> gifts, List<BusinessTrustUnits> trusts, List<LifePolicyReceipt> policies,
            List<ClaimedPolicy> claimed) {
    }

    private static NodeResult run(int year, RegimeCode regime, HeadEntries head) {
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
                        List.of(), List.of(), head.items(), head.accounts(), head.gifts(), head.trusts(),
                        head.policies(), head.claimed(), Optional.empty()),
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
                .containsExactly(RuleIds.OS_DIVIDEND, RuleIds.OS_BANKER_COMMISSION);
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
        // — the deemed dividend's 50,000 a nil line on the expenditure row, which does not reach it (D-323)
        assertThat(rowLines(result, RuleIds.OS_OTHER_EXPENDITURE)).extracting(TraceLine::in, TraceLine::out)
                .containsExactly(tuple(money("50000"), money("0")));
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
    void evaluate_whenEveryExpenseOnSecuritiesIsPayableOutsideIndiaUntaxed_nilsBothRows() {
        // Given — the commission row and the expenditure row both reach interest on securities
        OtherSourceItem untaxed = new OtherSourceItem(OtherSourceKind.INTEREST_ON_SECURITIES, Money.of("10000"),
                Money.of("100"), Money.of("200"), Money.of("400"), true);

        // When
        NodeResult result = run(2026, List.of(untaxed));

        // Then — S2 s.94(1)(b), (c): 100 and 200 + 400 each a nil line on its own row
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("10000")));
        assertThat(rowLines(result, RuleIds.OS_BANKER_COMMISSION)).extracting(TraceLine::in, TraceLine::out)
                .containsExactly(tuple(money("100"), money("0")));
        assertThat(rowLines(result, RuleIds.OS_OTHER_EXPENDITURE)).extracting(TraceLine::in, TraceLine::out)
                .containsExactly(tuple(money("600"), money("0")));
        assertThat(result.trace()).filteredOn(line -> !line.rule().equals(RuleIds.OS_INTEREST))
                .allSatisfy(line -> assertThat(line.limitedBy().orElseThrow()).contains("payable outside India untaxed"));
    }

    /**
     * {@code plans/W-153b.md} case 2, by hand from the rows' notes: an item of 10,000 bearing a commission of 100,
     * interest of 200 and other expenditure of 400 — distinct, so a row reaching the wrong kind changes a figure. Each
     * list is the (in, out) of that row's lines: the commission row reaches interest on securities (S2 s.93(1)(a)); the
     * expenditure row the interest kinds, family pension and the in-full kinds (s.93(1)(e)); the dividend-interest row
     * dividends and unit income, at 20% of 10,000 in TY 2025-26 and nil in TY 2026-27 (1961 s.57 proviso; s.93(2)). An
     * expense no row reaches is a nil line on the row it misses (W-153c2, D-323): the commission on the commission
     * row; interest and other expenditure on the expenditure row — for dividends and unit income other expenditure
     * only (400), their interest going to the year's limit.
     */
    private static Stream<Arguments> reachByKind() {
        List<OtherSourceKind> bearingExpenditureOnly = List.of(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST,
                OtherSourceKind.DEPOSIT_INTEREST, OtherSourceKind.OTHER_INTEREST, OtherSourceKind.FAMILY_PENSION,
                OtherSourceKind.KEYMAN_POLICY, OtherSourceKind.FORFEITED_ADVANCE,
                OtherSourceKind.TERMINATION_COMPENSATION, OtherSourceKind.OTHER);
        List<OtherSourceKind> bearingNothing = List.of(OtherSourceKind.DEEMED_DIVIDEND,
                OtherSourceKind.INTEREST_ON_COMPENSATION, OtherSourceKind.COMMUTED_FUND_PENSION,
                OtherSourceKind.DEATH_GRATUITY);
        List<Arguments> cases = new ArrayList<>();
        for (int year : new int[] {2025, 2026}) {
            List<Tuple> commissionRefused = List.of(tuple(money("100"), money("0")));
            List<Tuple> limited = List.of(tuple(money("200"), money(year == 2025 ? "200" : "0")));
            cases.add(Arguments.of(year, OtherSourceKind.INTEREST_ON_SECURITIES,
                    List.of(tuple(money("100"), money("100"))), List.of(tuple(money("600"), money("600"))), List.of()));
            bearingExpenditureOnly.forEach(kind -> cases.add(Arguments.of(year, kind,
                    List.of(tuple(money("100"), money("0"))), List.of(tuple(money("600"), money("600"))), List.of())));
            for (OtherSourceKind kind : List.of(OtherSourceKind.DIVIDEND, OtherSourceKind.UNIT_INCOME)) {
                cases.add(Arguments.of(year, kind, commissionRefused, List.of(tuple(money("400"), money("0"))),
                        limited));
            }
            bearingNothing.forEach(kind -> cases.add(Arguments.of(year, kind, commissionRefused,
                    List.of(tuple(money("600"), money("0"))), List.of())));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("reachByKind")
    void evaluate_whenAnItemBearsEveryExpense_eachRowReachesOnlyItsKinds(int year, OtherSourceKind kind,
            List<Tuple> commission, List<Tuple> expenditure, List<Tuple> dividendInterest) {
        // When
        NodeResult result = run(year, List.of(item(kind, "10000", "100", "200", "400")));

        // Then
        assertThat(rowLines(result, RuleIds.OS_BANKER_COMMISSION)).extracting(TraceLine::in, TraceLine::out)
                .containsExactlyElementsOf(commission);
        assertThat(rowLines(result, RuleIds.OS_OTHER_EXPENDITURE)).extracting(TraceLine::in, TraceLine::out)
                .containsExactlyElementsOf(expenditure);
        assertThat(rowLines(result, RuleIds.OS_DIVIDEND_INTEREST)).extracting(TraceLine::in, TraceLine::out)
                .containsExactlyElementsOf(dividendInterest);
    }

    private static List<TraceLine> rowLines(NodeResult result, RuleId rule) {
        return result.trace().stream().filter(line -> line.rule().equals(rule)).toList();
    }

    private static MoneyValue money(String amount) {
        return MoneyValue.of(Money.of(amount));
    }

    /**
     * {@code plans/W-153c2.md} case 3, by hand from the rows: the {@code RS-OS-SHARE} row an item of 10,000 meets and
     * what it deducts — half of interest on compensation (S2 s.93(1)(f)), the entire commuted pension or gratuity
     * ((g), (h)), the family pension's third under either regime's limit ((d)) rounded once to paise; no share for
     * any other built kind.
     */
    private static Stream<Arguments> shareByKind() {
        Map<OtherSourceKind, Tuple> shares = new EnumMap<>(OtherSourceKind.class);
        shares.put(OtherSourceKind.INTEREST_ON_COMPENSATION, tuple(RuleIds.OS_COMPENSATION_INTEREST, money("5000")));
        shares.put(OtherSourceKind.COMMUTED_FUND_PENSION, tuple(RuleIds.OS_COMMUTED_PENSION, money("10000")));
        shares.put(OtherSourceKind.DEATH_GRATUITY, tuple(RuleIds.OS_DEATH_GRATUITY, money("10000")));
        List<OtherSourceKind> built = Arrays.stream(OtherSourceKind.values()).filter(kind -> !List.of(
                OtherSourceKind.WINNINGS, OtherSourceKind.EMPLOYEE_CONTRIBUTIONS, OtherSourceKind.MACHINERY_LETTING,
                OtherSourceKind.MACHINERY_AND_BUILDING_LETTING).contains(kind)).toList();
        List<Arguments> cases = new ArrayList<>();
        for (int year : new int[] {2025, 2026}) {
            for (RegimeCode regime : List.of(OLD, NEW)) {
                Tuple pension = tuple(regime.equals(OLD) ? RuleIds.OS_FAMILY_PENSION
                        : RuleIds.OS_FAMILY_PENSION_NEW_TAX_REGIME, money("3333.33"));
                built.forEach(kind -> cases.add(Arguments.of(year, regime, kind, kind == OtherSourceKind.FAMILY_PENSION
                        ? List.of(pension) : Optional.ofNullable(shares.get(kind)).map(List::of).orElse(List.of()))));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "TY {0} {1} {2}")
    @MethodSource("shareByKind")
    void evaluate_whenAnItemIsCharged_deductsOnlyTheShareItsKindsRowStates(int year, RegimeCode regime,
            OtherSourceKind kind, List<Tuple> expected) {
        // When
        NodeResult result = run(year, regime, List.of(item(kind, "10000", "0", "0", "0")));

        // Then — the DEDUCTION lines with no expense entered are the share's alone
        assertThat(result.trace()).filteredOn(line -> line.kind() == StepKind.DEDUCTION)
                .extracting(TraceLine::rule, TraceLine::out).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenInterestOnCompensationBearsCommission_chargesItThenDeductsHalfAndNilsTheCommission(int year) {
        // When — 2,00,000 of interest on compensation, 1,000 to the banker
        NodeResult result = run(year, List.of(item(OtherSourceKind.INTEREST_ON_COMPENSATION, "200000", "1000", "0",
                "0")));

        // Then — charged in full by s.92(2)(i), half deducted by s.93(1)(f), the commission no row reaches
        assertThat(result.value()).isEqualTo(money("100000"));
        assertThat(result.trace()).extracting(TraceLine::rule, TraceLine::out).containsExactly(
                tuple(RuleIds.OS_COMPENSATION_INTEREST_INCOME, money("200000")),
                tuple(RuleIds.OS_COMPENSATION_INTEREST, money("100000")),
                tuple(RuleIds.OS_BANKER_COMMISSION, money("0")));
    }

    /**
     * Winnings (their rate deferred in the coverage register) and the kinds whose deductions are the business head's
     * (S2 s.93(1)(b), (c); D-202): no row of {@code RS-OS-INCOME} charges them, so they are refused by name (D-321).
     */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("uncharged")
    void evaluate_whenNoIncomeRowChargesTheKind_refusesItNamingKindSetAndYear(int year, OtherSourceKind kind) {
        // When / Then
        assertThatThrownBy(() -> run(year, List.of(item(kind, "100000", "0", "0", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-OS-INCOME charges " + kind + " in TY " + year + "-");
    }

    private static Stream<Arguments> uncharged() {
        return Stream.of(2025, 2026).flatMap(year -> Stream.of(OtherSourceKind.WINNINGS,
                OtherSourceKind.EMPLOYEE_CONTRIBUTIONS, OtherSourceKind.MACHINERY_LETTING,
                OtherSourceKind.MACHINERY_AND_BUILDING_LETTING).map(kind -> Arguments.of(year, kind)));
    }

    /**
     * {@code plans/W-153c1.md} case 2, by hand from the statute ({@code sources/2026-10-22-unit-income.md}): the row
     * of {@code RS-OS-INCOME} an item's kind selects — s.92(2)(e) interest, (a) dividends, (i) interest on
     * compensation, (d), (h), (j) their own rows, and s.92(1) the residue, unit income, family pension, commuted fund
     * pension and death gratuity; empty for the four uncharged kinds.
     */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("incomeRowByKind")
    void incomeRow_whenAnItemOfTheKindIsCharged_isTheRowItsKindSelects(int year, OtherSourceKind kind,
            Optional<RuleId> expected) {
        // When
        NodeContext ctx = contextFor(year);
        Optional<RuleId> chosen = ctx.optionalRuleOf(RuleSetIds.RS_OS_INCOME,
                OtherSourcesNode.factsOf(item(kind, "1000", "0", "0", "0"))).map(RuleRow::id);

        // Then
        assertThat(chosen).isEqualTo(expected);
    }

    /** The node's view in the year, the person the plain taxpayer, under the old regime. */
    private static NodeContext contextFor(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, OLD, TemporalContext.forTaxYear(taxYear,
                LocalDate.of(year, 9, 21)), EnteredFacts.none(), plain.assessee(), plain.family(), plain.ledgers(),
                plain.elections(), plain.capitalGains(), plain.filing());
        return NodeContext.forRun(pack, pack.regimes().get(OLD), inputs).forNode(new OtherSourcesNode());
    }

    private static Stream<Arguments> incomeRowByKind() {
        Map<OtherSourceKind, RuleId> rows = new EnumMap<>(OtherSourceKind.class);
        List.of(OtherSourceKind.INTEREST_ON_SECURITIES, OtherSourceKind.SAVINGS_DEPOSIT_INTEREST,
                OtherSourceKind.DEPOSIT_INTEREST, OtherSourceKind.OTHER_INTEREST)
                .forEach(kind -> rows.put(kind, RuleIds.OS_INTEREST));
        rows.put(OtherSourceKind.DIVIDEND, RuleIds.OS_DIVIDEND);
        rows.put(OtherSourceKind.DEEMED_DIVIDEND, RuleIds.OS_DIVIDEND);
        rows.put(OtherSourceKind.INTEREST_ON_COMPENSATION, RuleIds.OS_COMPENSATION_INTEREST_INCOME);
        rows.put(OtherSourceKind.KEYMAN_POLICY, RuleIds.OS_KEYMAN_POLICY);
        rows.put(OtherSourceKind.FORFEITED_ADVANCE, RuleIds.OS_FORFEITED_ADVANCE);
        rows.put(OtherSourceKind.TERMINATION_COMPENSATION, RuleIds.OS_TERMINATION_COMPENSATION);
        List.of(OtherSourceKind.OTHER, OtherSourceKind.UNIT_INCOME, OtherSourceKind.FAMILY_PENSION,
                OtherSourceKind.COMMUTED_FUND_PENSION, OtherSourceKind.DEATH_GRATUITY)
                .forEach(kind -> rows.put(kind, RuleIds.OS_RESIDUAL));
        return Stream.of(2025, 2026).flatMap(year -> Arrays.stream(OtherSourceKind.values())
                .map(kind -> Arguments.of(year, kind, Optional.ofNullable(rows.get(kind)))));
    }

    @Test
    void evaluate_whenUnitIncomeBearsInterestInTaxYear2025_chargesItOnTheResidualRow() {
        // Given — unit income of 1,00,000 with 10,000 of interest
        List<OtherSourceItem> items = List.of(item(OtherSourceKind.UNIT_INCOME, "100000", "0", "10000", "0"));

        // When
        NodeResult result = run(2025, items);

        // Then — s.92(1) / s.56(1) charges it (not "any dividend"); 20% of 1,00,000 = 20,000 ≥ 10,000
        assertThat(result.value()).isEqualTo(money("90000"));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.OS_RESIDUAL);
        assertThat(result.trace().getFirst().out()).isEqualTo(money("100000"));
    }

    @Test
    void evaluate_whenExpensesExceedTheIncome_isTheLossForSetOff() {
        // When — 10,000 of interest against 15,000 of expenditure
        NodeResult result = run(2026, List.of(item(OtherSourceKind.OTHER_INTEREST, "10000", "0", "0", "15000")));

        // Then — −5,000, which SetOffNode sets off against the other heads (D-217)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("-5000")));
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
        assertThat(result.trace()).extracting(TraceLine::rule, TraceLine::out).containsExactly(
                tuple(RuleIds.OS_RESIDUAL, money("30000")), tuple(RuleIds.OS_FAMILY_PENSION, money("10000")));
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
        assertThat(result.trace()).extracting(TraceLine::rule, TraceLine::out).containsExactly(
                tuple(RuleIds.OS_RESIDUAL, money("60000")),
                tuple(RuleIds.OS_FAMILY_PENSION_NEW_TAX_REGIME, money("20000")));
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
                .containsExactly(RuleIds.OS_RESIDUAL, RuleIds.OS_FAMILY_PENSION, RuleIds.OS_OTHER_EXPENDITURE);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAFundPensionIsCommuted_deductsTheEntireAmount(int year) {
        // When — S2 s.93(1)(g); 1961 s.10(10A)(iii)
        NodeResult result = run(year, OLD, List.of(item(OtherSourceKind.COMMUTED_FUND_PENSION, "500000", "0", "0",
                "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::rule, TraceLine::in, TraceLine::out).containsExactly(
                tuple(RuleIds.OS_RESIDUAL, money("500000"), money("500000")),
                tuple(RuleIds.OS_COMMUTED_PENSION, money("500000"), money("500000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAGovernmentGratuityIsReceivedOnDeath_deductsTheEntireAmount(int year) {
        // When — S2 s.93(1)(h) with s.19(2)(g); 1961 s.10(10)(i)
        NodeResult result = run(year, NEW, List.of(item(OtherSourceKind.DEATH_GRATUITY, "1000000", "0", "0", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.OS_RESIDUAL, RuleIds.OS_DEATH_GRATUITY);
    }

    @Test
    void evaluate_whenAnExpenseIsEnteredAgainstADeathGratuity_isANilLine() {
        // When — the entire amount is deducted already, and no expense row reaches a gratuity (D-320, D-323)
        NodeResult result = run(2026, OLD, List.of(item(OtherSourceKind.DEATH_GRATUITY, "1000000", "0", "0", "2000")));

        // Then — income 10,00,000, its share 10,00,000, the 2,000 a nil line on the expenditure row
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::rule, TraceLine::out).containsExactly(
                tuple(RuleIds.OS_RESIDUAL, money("1000000")), tuple(RuleIds.OS_DEATH_GRATUITY, money("1000000")),
                tuple(RuleIds.OS_OTHER_EXPENDITURE, money("0")));
        assertThat(result.trace().getLast().limitedBy().orElseThrow())
                .contains("expenditure is not deducted against DEATH_GRATUITY");
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
    void evaluate_whenASumAndPropertyAreBothReceivedFree_testsEachLimbsTotalApart(int year) {
        // When — a sum of 30,000 and shares worth 30,000, both for nothing
        NodeResult result = runGifts(year, sum("30000"), gift(GiftKind.OTHER_PROPERTY, "0", "30000"));

        // Then — limb (i)'s total 30,000 and limb (iii)(A)'s total 30,000 each do not exceed 50,000; pooled, the
        // 60,000 would have been charged
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.OS_GIFT, RuleIds.OS_GIFT_PROPERTY_FREE);
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
    void evaluate_whenTaxYear2025HasCovidDeathSumsFromTheEmployerAndOthers_poolsOnlyTheOthers() {
        // When — 3,00,000 from the deceased's employer (proviso (XIII)(A)) and 12,00,000 from others ((XIII)(B))
        NodeResult result = runGifts(2025, excluded(GiftExclusion.COVID_DEATH_FROM_EMPLOYER, "300000"),
                excluded(GiftExclusion.COVID_DEATH_FROM_OTHERS, "1200000"));

        // Then — (A) has no limit, so the employer's 3,00,000 is excluded whole; of the others' 12,00,000 the
        // limit excludes 10,00,000, and the 2,00,000 left exceeds 50,000, so 2,00,000 is charged
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("200000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.OS_GIFT_EXCLUSION, RuleIds.OS_GIFT_COVID_DEATH, RuleIds.OS_GIFT);
        assertThat(result.trace().getFirst().out()).isEqualTo(MoneyValue.of(Money.of("300000")));
        assertThat(result.trace().get(1).out()).isEqualTo(MoneyValue.of(Money.of("1000000")));
    }

    @Test
    void evaluate_whenTaxYear2025HasACovidTreatmentSum_excludesItWhole() {
        // When — 1,00,000 toward COVID-19 treatment (1961 proviso (XII))
        NodeResult result = runGifts(2025, excluded(GiftExclusion.COVID_TREATMENT, "100000"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.OS_GIFT_EXCLUSION);
        assertThat(result.trace().getFirst().out()).isEqualTo(MoneyValue.of(Money.of("100000")));
    }

    @Test
    void evaluate_whenTaxYear2026IsGivenACovidGround_isRefused() {
        // When / Then — the 2025 Act's s.92(3) has no such ground, so the year's exclusion row does not reach it
        assertThatThrownBy(() -> runGifts(2026, excluded(GiftExclusion.COVID_TREATMENT, "100000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COVID_TREATMENT, claimed for SUM_OF_MONEY, is not a ground OS-GIFT-EXCLUSION has"
                        + " in TY 2026-27");
    }

    @Test
    void evaluate_whenTaxYear2025ClaimsTheCovidDeathGroundForProperty_isRefused() {
        // Given — property, not a sum, claimed on 1961 proviso (XIII)(B), which reaches sums of money only
        GiftReceived property = new GiftReceived(GiftKind.OTHER_PROPERTY, Money.ZERO, Money.of("100000"),
                Optional.of(GiftExclusion.COVID_DEATH_FROM_OTHERS));

        // When / Then
        assertThatThrownBy(() -> runGifts(2025, property))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COVID_DEATH_FROM_OTHERS, claimed for OTHER_PROPERTY, is not a ground"
                        + " OS-GIFT-EXCLUSION has in TY 2025-26");
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

    /** A policy's receipt: not unit linked, not on death, not special, not IFSC, held alone unless said otherwise. */
    private static LifePolicyReceipt policy(LocalDate issuedOn, String received, String premium, String sumAssured,
            String unused) {
        return new LifePolicyReceipt(Money.of(received), issuedOn, false, false, Money.of(premium), Money.of(sumAssured),
                false, false, true, years(issuedOn), TaxYearId.of(years(issuedOn).startYear() + 9), Money.of(unused));
    }

    /** The tax year a policy issued on {@code issuedOn} first pays premium in. */
    private static TaxYearId years(LocalDate issuedOn) {
        return TaxYearId.containing(issuedOn);
    }

    private static NodeResult runPolicies(int year, LifePolicyReceipt... policies) {
        return run(year, OLD, List.of(), List.of(), List.of(), List.of(), List.of(policies));
    }

    private static final LocalDate IN_2015 = LocalDate.of(2015, 6, 1);
    private static final LocalDate IN_2023 = LocalDate.of(2023, 6, 1);

    /*
     * Life-policy sums (S2 Schedule II Sl. 2, s.92(2)(l), S4 r.59; 1961 s.10(10D), s.56(2)(xiii), r.11UACA; D-205),
     * from docs/taxplanning-build/sources/2026-10-17-life-policies.md. Worked by hand.
     */

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenThePremiumIsWithinTenPerCent_excludesTheSums(int year) {
        // When — 50,000 a year on 6,00,000 assured: 8.33%
        NodeResult result = runPolicies(year, policy(IN_2015, "800000", "50000", "600000", "0"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace().getFirst().kind()).isEqualTo(StepKind.EXEMPTION);
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.OS_LIFE_POLICY_RATIO);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenThePremiumIsAboveTenPerCent_chargesTheSumBeyondThePremiums(int year) {
        // When — 70,000 on 6,00,000: 11.67%; 10,00,000 received, 3,00,000 of premium not deducted
        NodeResult result = runPolicies(year, policy(IN_2015, "1000000", "70000", "600000", "300000"));

        // Then — r.59 A − B
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("700000")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.OS_LIFE_POLICY);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenASpecialPolicysPremiumIsWithinFifteenPerCent_excludesTheSums(int year) {
        // When — the same 11.67%, on the life of a person with a disability
        NodeResult result = runPolicies(year, new LifePolicyReceipt(Money.of("1000000"), IN_2015, false, false,
                Money.of("70000"), Money.of("600000"), true, false, true, TaxYearId.of(2015), TaxYearId.of(2024),
                Money.of("300000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAPolicyIssuedIn2010IsTestedAtTwentyPerCent_excludesOnlyWithin(int year) {
        // When — 90,000 on 5,00,000 (18%) and 1,10,000 on 5,00,000 (22%)
        LocalDate in2010 = LocalDate.of(2010, 6, 1);
        NodeResult result = runPolicies(year, policy(in2010, "500000", "90000", "500000", "400000"),
                policy(in2010, "500000", "110000", "500000", "400000"));

        // Then — the first excluded; the second 5,00,000 − 4,00,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenThePolicyPredatesApril2003_excludesWhateverThePremium(int year) {
        // When — 1,50,000 on 5,00,000: 30%, issued 2001
        NodeResult result = runPolicies(year, policy(LocalDate.of(2001, 6, 1), "800000", "150000", "500000", "0"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAPolicyFrom2023HasAPremiumAboveFiveLakh_chargesTheSumBeyondThePremiums(int year) {
        // When — 6,00,000 a year on 1,00,00,000 (6%), 40,00,000 received, 30,00,000 not deducted
        NodeResult result = runPolicies(year, policy(IN_2023, "4000000", "600000", "10000000", "3000000"));

        // Then — the 5,00,000 limit breached: 10,00,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1000000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheSumsAreReceivedOnADeath_excludesThem(int year) {
        // When — the same policy, on the insured's death
        NodeResult result = runPolicies(year, new LifePolicyReceipt(Money.of("4000000"), IN_2023, false, true,
                Money.of("600000"), Money.of("10000000"), false, false, true, TaxYearId.of(2023), TaxYearId.of(2032),
                Money.of("3000000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAUnitLinkedPolicyIsNotExcluded_refusesItAsCapitalGains(int year) {
        // When / Then — a ULIP from 2022 at 3,00,000 a year, above 2,50,000
        LifePolicyReceipt ulip = new LifePolicyReceipt(Money.of("2000000"), LocalDate.of(2022, 6, 1), true, false,
                Money.of("300000"), Money.of("5000000"), false, false, true, TaxYearId.of(2022), TaxYearId.of(2031),
                Money.of("1500000"));
        assertThatThrownBy(() -> runPolicies(year, ulip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W-088");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheSumsDoNotExceedThePremiums_chargesNothing(int year) {
        // When — 10,00,000 received against 12,00,000 of premium not deducted
        NodeResult result = runPolicies(year, policy(IN_2015, "1000000", "70000", "600000", "1200000"));

        // Then — "the sum exceeding": nil, never a loss
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAnIfscOfficeIssuedTheCappedPolicyIn2025_excludesTheSums(int year) {
        // When — 6,00,000 a year on 1,00,00,000, issued 1-5-2025 by an IFSC office
        NodeResult result = runPolicies(year, new LifePolicyReceipt(Money.of("4000000"), LocalDate.of(2025, 5, 1),
                false, false, Money.of("600000"), Money.of("10000000"), false, true, true, TaxYearId.of(2025),
                TaxYearId.of(2034), Money.of("3000000")));

        // Then — no premium limit (TY 2026-27 from 1-4-2025; TY 2025-26 any date); 6% within 10%
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenALifePolicySitsBesideInterest_addsBoth(int year) {
        // When
        NodeResult result = run(year, OLD, List.of(item(OtherSourceKind.INTEREST_ON_SECURITIES, "10000", "0", "0",
                "0")), List.of(), List.of(), List.of(), List.of(policy(IN_2015, "1000000", "70000", "600000",
                "300000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("710000")));
    }

    /** A capped policy's receipt — not unit linked, issued on {@code issuedOn}, sum assured ten times the premium. */
    private static LifePolicyReceipt capped(LocalDate issuedOn, int fromYear, int toYear, String premium,
            String received, String unused, boolean claims) {
        Money annual = Money.of(premium);
        return new LifePolicyReceipt(Money.of(received), issuedOn, false, false, annual,
                Money.of(annual.amountInRupees().multiply(BigDecimal.TEN).toPlainString()), false, false,
                claims, TaxYearId.of(fromYear), TaxYearId.of(toYear), Money.of(unused));
    }

    private static ClaimedPolicy old(LocalDate issuedOn, boolean unitLinked, String premium, int fromYear, int toYear) {
        return new ClaimedPolicy(issuedOn, unitLinked, Money.of(premium), TaxYearId.of(fromYear), TaxYearId.of(toYear));
    }

    private static NodeResult runCapped(int year, List<ClaimedPolicy> claimed, LifePolicyReceipt... receipts) {
        return run(year, OLD, new HeadEntries(List.of(), List.of(), List.of(), List.of(), List.of(receipts), claimed));
    }

    private static final LocalDate APRIL_2023 = LocalDate.of(2023, 4, 1);
    private static final LocalDate APRIL_2024 = LocalDate.of(2024, 4, 1);

    /*
     * Several capped policies, from the examples of CBDT Circular No. 15/2023 (other policies, ₹5,00,000), continued
     * by S2 s.536(2)(j) (D-207; docs/taxplanning-build/sources/2026-10-17-capped-policies.md A3). Premium years run
     * from issue to the year before maturity; received and unused premium are fixtures.
     */

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOnePolicyIsOverTheLimitAlone_chargesItAndExcludesTheOther(int year) {
        // When — Ex. 4: A 4,50,000 and B 5,50,000, both claimed
        NodeResult result = runCapped(year, List.of(),
                capped(APRIL_2023, 2023, 2032, "450000", "5200000", "4500000", true),
                capped(APRIL_2023, 2023, 2032, "550000", "6000000", "5500000", true));

        // Then — B over 5,00,000 alone: 60,00,000 − 55,00,000 = 5,00,000; A excluded
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("500000")));
        assertThat(result.trace()).extracting(TraceLine::kind).containsExactly(StepKind.EXEMPTION, StepKind.INCOME);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheClaimedSetIsWithinTheLimit_excludesEach(int year) {
        // When — Ex. 5: A 1,00,000 and B 3,50,000 claimed; C 6,00,000 over alone
        NodeResult result = runCapped(year, List.of(),
                capped(APRIL_2023, 2023, 2032, "100000", "1200000", "1000000", true),
                capped(APRIL_2023, 2023, 2032, "350000", "4000000", "3500000", true),
                capped(APRIL_2023, 2023, 2032, "600000", "7000000", "6000000", true));

        // Then — 4,50,000 within 5,00,000: A and B excluded; C 70,00,000 − 60,00,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1000000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenSeveralClaimsTogetherBreachTheLimit_refusesForThePersonToChoose(int year) {
        // When / Then — 1,00,000 + 3,50,000 + 2,00,000 = 6,50,000, all claimed
        assertThatThrownBy(() -> runCapped(year, List.of(),
                        capped(APRIL_2023, 2023, 2032, "100000", "1200000", "1000000", true),
                        capped(APRIL_2023, 2023, 2032, "350000", "4000000", "3500000", true),
                        capped(APRIL_2023, 2023, 2032, "200000", "2400000", "2000000", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("choose which to claim");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenASingleClaimBreachesWithAnOldPolicy_chargesIt(int year) {
        // When — Ex. 7: old X 4,50,000 (2023-24 to 2032-33); A 1,00,000 from 2024-25 claimed
        NodeResult result = runCapped(year, List.of(old(APRIL_2023, false, "450000", 2023, 2032)),
                capped(APRIL_2024, 2024, 2033, "100000", "1200000", "1000000", true));

        // Then — 5,50,000 in 2024-25: not excluded; 12,00,000 − 10,00,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("200000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAClaimWithAnOldPolicyStaysWithinTheLimit_excludesIt(int year) {
        // When — Ex. 9: old X 2,50,000; B 2,50,000 claimed
        NodeResult result = runCapped(year, List.of(old(APRIL_2023, false, "250000", 2023, 2032)),
                capped(APRIL_2024, 2024, 2033, "250000", "3800000", "2500000", true));

        // Then — 5,00,000 does not exceed
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenThePremiumYearsDoNotOverlap_excludesBoth(int year) {
        // When — Ex. 8: old X 5,00,000 for 2023-24 to 2033-34; A 5,00,000 for 2034-35 to 2047-48
        NodeResult result = runCapped(year, List.of(old(APRIL_2023, false, "500000", 2023, 2033)),
                capped(LocalDate.of(2034, 4, 1), 2034, 2047, "500000", "5200000", "5000000", true));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAnOldUlipIsHeld_testsOtherPoliciesApart(int year) {
        // When — Ex. 13: old ULIP X 1,00,000 and old policy A 1,00,000; policy C 1,50,000 claimed
        NodeResult result = runCapped(year, List.of(old(LocalDate.of(2021, 4, 1), true, "100000", 2021, 2032),
                        old(APRIL_2023, false, "100000", 2023, 2032)),
                capped(APRIL_2023, 2023, 2032, "150000", "1800000", "1500000", true));

        // Then — A + C = 2,50,000 within 5,00,000; the ULIP counts toward its own class only
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenACappedPolicyIsNotClaimed_chargesIt(int year) {
        // When — within every limit, but not claimed exempt
        NodeResult result = runCapped(year, List.of(),
                capped(APRIL_2023, 2023, 2032, "100000", "1200000", "1000000", false));

        // Then — 12,00,000 − 10,00,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("200000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAnOldPolicyIsNotOfACappedClass_isRefused(int year) {
        // When / Then — a 2015 policy is outside the caps
        assertThatThrownBy(() -> runCapped(year, List.of(old(LocalDate.of(2015, 4, 1), false, "100000", 2015, 2024)),
                        capped(APRIL_2023, 2023, 2032, "100000", "1200000", "1000000", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not of a capped class");
    }
}
