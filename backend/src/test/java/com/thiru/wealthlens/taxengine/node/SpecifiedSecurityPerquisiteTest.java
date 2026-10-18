package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.EmployerShareAllotment;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FairMarketValueBasis;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Specified securities and sweat equity (S2 s.17(1)(d), (4)(h); S4 r.15(6)–(8); 1961 s.17(2)(vi); Rules 1962
 * r.3(8)–(9); D-121): the fair market value on the exercise date less the price paid. Each figure is worked by hand;
 * the merchant banker's window is 180 days.
 */
class SpecifiedSecurityPerquisiteTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static EmployerShareAllotment allotment(int year, int units, String paid, FairMarketValueBasis basis) {
        LocalDate day = LocalDate.of(year, 9, 1);
        return new EmployerShareAllotment(0, day, day, units, Money.of(paid), basis);
    }

    private static NodeResult run(int year, List<EmployerShareAllotment> allotments) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())),
                        List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        allotments, List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(NEW_TAX_REGIME)
                .inputs(inputs)
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_ACCOM, List.of(Money.ZERO))))
                .given(NodeIds.SALARY_EXEMPTIONS, MoneyValue.of(Money.ZERO))
                .given(NodeIds.SALARY_MONETARYINCOME, MoneyValue.of(Money.ZERO))
                .run(new PerquisiteNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAListedShareTradedOnTheExerciseDate_isTheAverageLessThePricePaid(int year) {
        // When — (510 + 490) ÷ 2 = 500; (500 − 100) × 1,000
        NodeResult result = run(year, List.of(allotment(year, 1000, "100",
                new FairMarketValueBasis.ListedTraded(Money.of("510"), Money.of("490")))));

        // Then — by any monetary salary at all: every employee
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("400000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
    }

    @Test
    void evaluate_whenTheAverageHasHalfAPaisa_roundsOnceOverTheWhole() {
        // When — (100.01 + 100.00) × 3 ÷ 2 = 300.015 → 300.02, less nothing paid
        NodeResult result = run(2026, List.of(allotment(2026, 3, "0",
                new FairMarketValueBasis.ListedTraded(Money.of("100.01"), Money.of("100.00")))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("300.02")));
    }

    @Test
    void evaluate_whenTheShareDidNotTrade_usesTheClosestPrecedingClose() {
        // When — (480 − 100) × 1,000
        NodeResult result = run(2026, List.of(allotment(2026, 1000, "100",
                new FairMarketValueBasis.ListedNotTraded(Money.of("480"), LocalDate.of(2026, 8, 29)))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("380000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenUnlistedWithAMerchantBankersValue90DaysBefore_isThatValueLessThePrice(int year) {
        // When — (250 − 10) × 2,000
        NodeResult result = run(year, List.of(allotment(year, 2000, "10",
                new FairMarketValueBasis.MerchantBankerValue(Money.of("250"), LocalDate.of(year, 9, 1).minusDays(90)))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("480000")));
    }

    @Test
    void evaluate_whenTheMerchantBankersDateIsExactly180DaysBefore_isAccepted() {
        // When
        NodeResult result = run(2026, List.of(allotment(2026, 1, "0",
                new FairMarketValueBasis.MerchantBankerValue(Money.of("250"), LocalDate.of(2026, 9, 1).minusDays(180)))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("250")));
    }

    @Test
    void evaluate_whenTheMerchantBankersDateIs181DaysBefore_isRefused() {
        // When / Then
        assertThatThrownBy(() -> run(2026, List.of(allotment(2026, 1, "0",
                        new FairMarketValueBasis.MerchantBankerValue(Money.of("250"),
                                LocalDate.of(2026, 9, 1).minusDays(181))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("180 days");
    }

    @Test
    void evaluate_whenMoreWasPaidThanTheValue_isNil() {
        // When
        NodeResult result = run(2026, List.of(allotment(2026, 100, "600",
                new FairMarketValueBasis.ListedTraded(Money.of("510"), Money.of("490")))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenAllottedOutsideTheTaxYear_isRefused() {
        // Given — allotted in TY 2025-26, computed for TY 2026-27
        LocalDate day = LocalDate.of(2026, 3, 1);
        EmployerShareAllotment earlier = new EmployerShareAllotment(0, day, day, 1, Money.ZERO,
                new FairMarketValueBasis.ListedTraded(Money.of("10"), Money.of("10")));

        // When / Then
        assertThatThrownBy(() -> run(2026, List.of(earlier)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allotted");
    }

    @Test
    void construct_whenExercisedAfterAllotment_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new EmployerShareAllotment(0, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 1,
                        Money.ZERO, new FairMarketValueBasis.ListedTraded(Money.of("10"), Money.of("10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exercised");
    }
}
