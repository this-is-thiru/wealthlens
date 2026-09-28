package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
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
 * s.13; D-126). Figures by hand, the first being {@code DR 01} R13's worked example.
 */
class EmployerContributionNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static EmployerFundContribution to(int employment, EmployerFund fund, String amount) {
        return new EmployerFundContribution(employment, fund, Money.of(amount));
    }

    private static NodeResult run(int year, List<EmployerFundContribution> contributions) {
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
                        List.of(), List.of(), List.of(), contributions),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(NEW_TAX_REGIME).inputs(inputs).run(new EmployerContributionNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheWorkedExample_isTheExcessOverSevenLakhFifty(int year) {
        // When — 2,88,000 + 3,36,000 + 2,00,000 = 8,24,000
        NodeResult result = run(year, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "288000"),
                to(0, EmployerFund.NOTIFIED_PENSION_SCHEME, "336000"),
                to(0, EmployerFund.APPROVED_SUPERANNUATION_FUND, "200000")));

        // Then — 8,24,000 − 7,50,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("74000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_EMPLOYER_CONTRIBUTION);
            assertThat(line.provenance().verificationLevel()).isEqualTo(year == 2025
                    ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
        });
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
        // When — 4,00,000 + 4,00,000 = 8,00,000
        NodeResult result = run(2026, List.of(to(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, "400000"),
                to(1, EmployerFund.NOTIFIED_PENSION_SCHEME, "400000")));

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
