package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
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
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Income from other sources: interest and dividends (S2 ss.92–94; 1961 ss.56–58; D-194). Figures by hand from the
 * sources quoted in {@code docs/taxplanning-build/sources/2026-10-16-other-sources.md}: the 20% of the 1961 s.57
 * proviso (FA 2020 s.30(b)), nil from TY 2026-27 (FA 2026 s.44(b)), 50% of interest on compensation.
 */
class OtherSourcesNodeTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static OtherSourceItem item(OtherSourceKind kind, String received, String commission, String interest,
            String other) {
        return new OtherSourceItem(kind, Money.of(received), Money.of(commission), Money.of(interest), Money.of(other),
                false);
    }

    private static NodeResult run(int year, List<OtherSourceItem> items) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, OLD,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                        List.of(), List.of(), items),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(OLD).inputs(inputs).run(new OtherSourcesNode());
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

    @Test
    void evaluate_whenAnotherKindIsEntered_refusesItNamingTheRowThatBuildsIt() {
        // When / Then
        assertThatThrownBy(() -> run(2026, List.of(item(OtherSourceKind.OTHER, "100000", "0", "0", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W-079g");
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
            item(OtherSourceKind.OTHER_INTEREST, "14000", "0", "0", "0"),
            item(OtherSourceKind.OTHER_INTEREST, "90000", "0", "0", "0"),
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
}
