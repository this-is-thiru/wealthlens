package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tax already paid taken off the bill (W-089b, D-436; 2025 s.266(2)(a), (b), s.516; 1961 s.140A(1), s.288B). Every figure is
 * worked by hand in {@code plans/W-089b.md}.
 */
class CreditsAndBalanceTest {

    private static final LocalDate DUE = LocalDate.of(2027, 7, 31);

    private static final RegimeCode REGIME = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    private static EngineInputs paying(int year, FilingFacts filing) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(taxYear, REGIME, TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)),
                plain.entered(), plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), filing);
    }

    private static FilingFacts.Payment paid(String amount) {
        return new FilingFacts.Payment(LocalDate.of(2026, 12, 15), Money.of(amount));
    }

    private static NodeResult credits(int year, FilingFacts filing) {
        return NodeHarness.with(pack(year)).regime(REGIME).inputs(paying(year, filing)).run(new CreditsNode());
    }

    private static Money balance(String bill, String credits) {
        return ((MoneyValue) NodeHarness.with(pack(2026)).regime(REGIME)
                .inputs(paying(2026, FilingFacts.notYetFurnished(DUE))).given(NodeIds.TAX_NETPAYABLE, Money.of(bill)).given(NodeIds.CREDITS, Money.of(credits))
                .run(new BalanceNode()).value()).amount();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void credits_whenAllThreeArePaid_sumsThemWithALineEach(int year) {
        // Given — at source 50,000; advance 15,000 + 5,000 = 20,000; on the return 5,000
        FilingFacts filing = new FilingFacts(DUE, Optional.empty(), List.of(paid("15000"), paid("5000")),
                Money.of("50000"), List.of(paid("5000")));

        // When
        NodeResult result = credits(year, filing);

        // Then — 75,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("75000")));
        assertThat(result.trace()).hasSize(3).allSatisfy(line -> assertThat(line.rule()).isEqualTo(RuleIds.CREDIT_TAX_PAID));
        assertThat(result.trace()).extracting(line -> line.limitedBy().orElseThrow())
                .containsExactly("tax at source", "advance tax", "self-assessment tax");
    }

    @Test
    void credits_whenNothingIsPaid_isNil() {
        // When / Then
        assertThat(credits(2026, FilingFacts.notYetFurnished(DUE)).value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void credits_whenAnAdvancePaymentIsAfterTheYear_stillCountsIt() {
        // Given — paid after 31 March: still tax already paid (s.266(2)(a)); only interest turns on the date (W-112)
        FilingFacts filing = new FilingFacts(DUE, Optional.empty(),
                List.of(new FilingFacts.Payment(LocalDate.of(2027, 6, 1), Money.of("20000"))), Money.ZERO, List.of());

        // When / Then
        assertThat(credits(2026, filing).value()).isEqualTo(MoneyValue.of(Money.of("20000")));
    }

    @Test
    void filingFacts_whenTaxAtSourceIsNegative_refuses() {
        // When / Then
        assertThatThrownBy(() -> new FilingFacts(DUE, Optional.empty(), List.of(), Money.of("-1"), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("taxAtSource");
    }

    @ParameterizedTest(name = "{0} − {1} = {2}")
    @CsvSource({"87750, 0, 87750", "87750, 75000, 12750", "87750, 100004, -12254"})
    void balance_whenCreditsAreTaken_isTheBillLessThem(String bill, String credits, String expected) {
        // When / Then
        assertThat(balance(bill, credits)).isEqualTo(Money.of(expected));
    }

    @ParameterizedTest(name = "{0} rounds to {1}, TY {2}")
    @CsvSource({"12750, 12750, 2026", "12753, 12750, 2026", "12756, 12760, 2026", "-12254, -12250, 2026",
            "-12260, -12260, 2026", "-12255, -12260, 2026", "12753, 12750, 2025", "-12255, -12260, 2025"})
    void roundNetPayable_whenPayableOrRefundable_roundsToTheNearestTen(String balance, String expected, int year) {
        // When
        NodeResult result = NodeHarness.with(pack(year)).regime(REGIME)
                .inputs(paying(year, FilingFacts.notYetFurnished(DUE))).given(NodeIds.TAX_BALANCE, Money.of(balance))
                .run(new RoundNetPayableNode());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(expected)));
    }
}
