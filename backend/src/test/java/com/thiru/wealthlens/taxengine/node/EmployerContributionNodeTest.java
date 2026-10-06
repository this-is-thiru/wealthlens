package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.RetirementFundHistory;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The aggregate employer contribution beyond ₹7,50,000 (S2 s.17(1)(h); 1961 s.17(2)(vii) as substituted by FA 2020
 * s.13; D-126) and the accretion on it (S2 s.17(1)(i), S4 r.16; 1961 s.17(2)(viia), Rules 1962 r.3B; D-127). Figures
 * by hand, the first being {@code DR 01} R13's worked example.
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

    private static NodeResult run(int year, List<EmployerFundContribution> contributions,
            Optional<RetirementFundHistory> history) {
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
                        List.of(), List.of(), List.of(), contributions, history, List.of(), List.of(), List.of(), List.of(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(NEW_TAX_REGIME).inputs(inputs).run(new EmployerContributionNode());
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
}
