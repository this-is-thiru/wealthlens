package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.RecognisedFundInterest;
import com.thiru.wealthlens.taxengine.input.RetirementFundHistory;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The aggregate employer contribution beyond ₹7,50,000 (S2 s.17(1)(h); 1961 s.17(2)(vii) as substituted by FA 2020
 * s.13; D-126) and the accretion on it (S2 s.17(1)(i), S4 r.16; 1961 s.17(2)(viia), Rules 1962 r.3B; D-127). Figures
 * by hand, the first being {@code DR 01} R13's worked example. The recognised provident fund's own charges — the
 * employer's contribution beyond twelve per cent of salary (1961 Sch. IV Pt A r.6(a), TY 2025-26 only) and interest
 * above the notified 9.5 per cent (r.6(b); S2 Sch. XI Pt A para 6 as substituted by FA 2026 s.127) — are D-151's.
 */
class EmployerContributionNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static EmployerFundContribution to(int employment, EmployerFund fund, String amount) {
        return new EmployerFundContribution(employment, fund, Money.of(amount));
    }

    /** R13's example: 2,88,000 + 3,36,000 + 2,00,000 = 8,24,000, an excess PC of 74,000. */
    private static final List<EmployerFundContribution> WORKED_EXAMPLE = List.of(
            to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "288000"),
            to(0, EmployerFund.NOTIFIED_PENSION_SCHEME, "336000"),
            to(0, EmployerFund.APPROVED_SUPERANNUATION_FUND, "200000"));

    /** PC1 + TP1 = 1,00,000; I 90,000; F(avg) = (9,00,000 + 11,00,000) ÷ 2 = 10,00,000, so R = 9%. */
    private static final RetirementFundHistory NINE_PER_CENT = history("60000", "40000", "90000", "900000", "1100000");

    private static RetirementFundHistory history(String earlierExcess, String earlierTaxed, String income,
            String opening, String closing) {
        return new RetirementFundHistory(Money.of(earlierExcess), Money.of(earlierTaxed), Money.of(income),
                Money.of(opening), Money.of(closing));
    }

    private static NodeResult run(int year, List<EmployerFundContribution> contributions) {
        return run(year, contributions, Optional.empty());
    }

    /** R13's basic of 24,00,000 in both employments, so its 2,88,000 is exactly twelve per cent. */
    private static final List<Money> R13_SALARY = List.of(Money.of("2400000"), Money.of("2400000"));

    private static NodeResult run(int year, List<EmployerFundContribution> contributions,
            Optional<RetirementFundHistory> history) {
        return run(year, contributions, history, List.of(), R13_SALARY);
    }

    private static NodeResult run(int year, List<EmployerFundContribution> contributions,
            Optional<RetirementFundHistory> history, List<RecognisedFundInterest> interest,
            List<Money> providentFundSalary) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        List<Employment> employments = Collections.nCopies(2,
                new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty()));
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(employments, List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), contributions, history, interest, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(NEW_TAX_REGIME).inputs(inputs)
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_RPF, providentFundSalary)))
                .run(new EmployerContributionNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheWorkedExample_isTheExcessOverSevenLakhFifty(int year) {
        // When — the funds earned nothing this year, so the accretion is nil
        NodeResult result = run(year, WORKED_EXAMPLE,
                Optional.of(history("0", "0", "0", "900000", "1100000")));

        // Then — 8,24,000 − 7,50,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("74000")));
        assertThat(result.trace()).first().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_EMPLOYER_CONTRIBUTION);
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("74000")));
            assertThat(line.provenance().verificationLevel()).isEqualTo(year == 2025
                    ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheFundsEarnedNinePerCent_addsTheAccretionOnTheExcess(int year) {
        // When
        NodeResult result = run(year, WORKED_EXAMPLE, Optional.of(NINE_PER_CENT));

        // Then — TP = (74,000 ÷ 2) × 9% + 1,00,000 × 9% = 3,330 + 9,000 = 12,330; 74,000 + 12,330
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("86330")));
        assertThat(result.trace()).element(1).satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_EMPLOYER_ACCRETION);
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("12330")));
            assertThat(line.provenance().verificationLevel()).isEqualTo(year == 2025
                    ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
        });
    }

    @Test
    void evaluate_whenEarlierYearsExceedTheOpeningBalance_ignoresTheExcess() {
        // Given — PC1 + TP1 = 10,00,000 + 2,00,000 = 12,00,000 against an opening balance of 9,00,000
        RetirementFundHistory history = history("1000000", "200000", "90000", "900000", "1100000");

        // When
        NodeResult result = run(2026, WORKED_EXAMPLE, Optional.of(history));

        // Then — r.16(2)(b): (37,000 + 9,00,000) × 9% = 84,330; 74,000 + 84,330
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("158330")));
    }

    @Test
    void evaluate_whenOnlyEarlierYearsExceeded_chargesTheirAccretionAlone() {
        // When — nothing contributed this year
        NodeResult result = run(2026, List.of(), Optional.of(NINE_PER_CENT));

        // Then — PC nil: 1,00,000 × 9%
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("9000")));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.PERQ_EMPLOYER_ACCRETION));
    }

    @Test
    void evaluate_whenAnExcessHasNoFundHistory_isRefused() {
        // When / Then — the accretion cannot be nil by default
        assertThatThrownBy(() -> run(2026, WORKED_EXAMPLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retirement fund history");
    }

    @Test
    void evaluate_whenBothBalancesAreNil_isRefused() {
        // When / Then — F(avg) nil cannot divide I
        assertThatThrownBy(() -> run(2026, WORKED_EXAMPLE, Optional.of(history("0", "0", "90000", "0", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("F(avg)");
    }

    @Test
    void evaluate_whenExactlyTheLimit_isNil() {
        // When — "in excess of ₹ 750000"
        NodeResult result = run(2026, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "750000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenTwoEmployersContribute_addsThemTogether() {
        // When — 4,00,000 + 4,00,000 = 8,00,000; the funds earned nothing, so the accretion is nil
        NodeResult result = run(2026, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "400000"),
                to(1, EmployerFund.NOTIFIED_PENSION_SCHEME, "400000")),
                Optional.of(history("0", "0", "0", "900000", "1100000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("50000")));
    }

    @Test
    void evaluate_whenNothingIsContributed_isNilAndUntraced() {
        // When
        NodeResult result = run(2026, List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void evaluate_whenAContributionNamesAnEmploymentNotEntered_isRefused() {
        // When / Then
        assertThatThrownBy(() -> run(2026, List.of(to(5, EmployerFund.RECOGNISED_PROVIDENT_FUND, "100000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("employment 5");
    }

    private static NodeResult runProvidentFund(int year, List<EmployerFundContribution> contributions,
            List<Money> salary) {
        return run(year, contributions, Optional.empty(), List.of(), salary);
    }

    private static NodeResult runInterest(int year, String credited, String ratePercent) {
        return run(year, List.of(), Optional.empty(),
                List.of(new RecognisedFundInterest(0, Money.of(credited), Rate.percent(ratePercent))), R13_SALARY);
    }

    @Test
    void evaluate_whenT4Dash3InTaxYear2025_chargesTheContributionBeyondTwelvePerCent() {
        // When — DR 08 T4-3: basic 10,00,000, employer PF 1,50,000; the aggregate stays under 7,50,000
        NodeResult result = runProvidentFund(2025, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "150000")),
                List.of(Money.of("1000000"), Money.ZERO));

        // Then — 1,50,000 − 12% of 10,00,000 = 1,50,000 − 1,20,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
        assertThat(result.trace()).element(1).satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_RPF_CONTRIBUTION);
            assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("150000")));
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("30000")));
            assertThat(line.provenance().verificationLevel()).isEqualTo(VerificationLevel.UNVERIFIED);
        });
    }

    @Test
    void evaluate_whenT4Dash3InTaxYear2026_chargesNothing() {
        // When — the same facts after the Finance Act, 2026 s.127(a)(iii) removed the twelve per cent limb
        NodeResult result = runProvidentFund(2026, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "150000")),
                List.of(Money.of("1000000"), Money.ZERO));

        // Then — the aggregate alone, nil
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.PERQ_EMPLOYER_CONTRIBUTION));
    }

    @ParameterizedTest(name = "salary {0} → {1}")
    @CsvSource({"1000000, 30000", "800000, 54000"})
    void evaluate_whenTaxYear2025_measuresTwelvePerCentOfTheSalaryBase(String salary, String charged) {
        // When — 1,50,000 contributed; the base is basic 8,00,000 + DA 2,00,000 where the terms provide DA, and
        // 8,00,000 where they do not (SalaryBaseNodeTest measures it)
        NodeResult result = runProvidentFund(2025, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "150000")),
                List.of(Money.of(salary), Money.ZERO));

        // Then — 1,50,000 − 1,20,000; 1,50,000 − 96,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(charged)));
    }

    @Test
    void evaluate_whenRecognisedFundContributionsAreExactlyTwelvePerCent_isNil() {
        // When — R13's example: 2,88,000 on 24,00,000
        NodeResult result = runProvidentFund(2025, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "288000")),
                R13_SALARY);

        // Then — "in excess of twelve per cent."
        assertThat(result.trace()).element(1).satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_RPF_CONTRIBUTION);
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
        });
    }

    @Test
    void evaluate_whenTwoEmployersEachHaveAFund_measuresEachAgainstItsOwnSalary() {
        // When — A: 90,000 on 6,00,000; B: 40,000 on 4,00,000
        NodeResult result = runProvidentFund(2025, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "90000"),
                to(1, EmployerFund.RECOGNISED_PROVIDENT_FUND, "40000")),
                List.of(Money.of("600000"), Money.of("400000")));

        // Then — A 90,000 − 72,000 = 18,000; B 40,000 − 48,000, nil; pooled it would be 1,30,000 − 1,20,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("18000")));
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.PERQ_RPF_CONTRIBUTION))
                .extracting(line -> line.out())
                .containsExactly(MoneyValue.of(Money.of("18000")), MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenAContributionGoesToAnotherFund_leavesItOutOfTheTwelvePerCent() {
        // When — a pension-scheme contribution far beyond 12% of a nil salary
        NodeResult result = runProvidentFund(2025, List.of(to(0, EmployerFund.NOTIFIED_PENSION_SCHEME, "100000")),
                List.of(Money.ZERO, Money.ZERO));

        // Then — under the aggregate's limit, and no provident fund line
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).noneMatch(line -> line.rule().equals(RuleIds.PERQ_RPF_CONTRIBUTION));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheFundAllowedElevenPerCent_chargesTheInterestAboveNineAndAHalf(int year) {
        // When — 1,10,000 credited at 11%
        NodeResult result = runInterest(year, "110000", "11");

        // Then — on a balance of 1,10,000 ÷ 11% = 10,00,000, 9.5% is 95,000; 1,10,000 − 95,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_RPF_INTEREST);
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("15000")));
            assertThat(line.provenance().verificationLevel()).isEqualTo(VerificationLevel.UNVERIFIED);
        });
    }

    @ParameterizedTest(name = "{0}%")
    @ValueSource(strings = {"8.25", "9.5"})
    void evaluate_whenTheFundAllowedNoMoreThanTheNotifiedRate_chargesNothing(String ratePercent) {
        // When — 82,500 credited
        NodeResult result = runInterest(2026, "82500", ratePercent);

        // Then — only "a rate exceeding" 9.5% is charged: none of it
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenInterestIsCreditedAtANilRate_isRefused() {
        // When / Then — no balance yields interest at nil
        assertThatThrownBy(() -> runInterest(2026, "10000", "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate of nil");
    }
}
