package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.MedicalTreatmentAbroad;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
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
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Medical treatment abroad paid for by the employer (S2 s.17(2)(f), (3); 1961 s.17(2) proviso (vi), (vii); D-112).
 * The threshold is ₹8,00,000 in both years — Rules 2026 r.19 ("₹ 800000") and Rules 1962 r.3D ("eight lakh rupees",
 * G.S.R. 555(E)) — and "does not exceed" is inclusive. Each figure is worked by hand.
 */
class MedicalTreatmentAbroadNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static MedicalTreatmentAbroad claim(String treatmentAndStay, String travel, String permitted) {
        return new MedicalTreatmentAbroad(0, Money.of(treatmentAndStay), Money.of(travel), Money.of(permitted), 1);
    }

    private static NodeResult run(int year, RegimeCode regime, String incomeBeforeTravel,
            List<MedicalTreatmentAbroad> claims) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())), List.of(),
                        List.of(), List.of(), Optional.empty(), List.of(), List.of(), claims, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.SALARY_INCOMEBEFORETRAVEL, MoneyValue.of(Money.of(incomeBeforeTravel)))
                .run(new MedicalTreatmentAbroadNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTreatmentExceedsWhatTheReserveBankPermitted_theExcessIsSalary(int year) {
        // When — 6,00,000 spent, 5,00,000 permitted; no travel
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "750000", List.of(claim("600000", "0", "500000")));

        // Then — 6,00,000 − 5,00,000 = 1,00,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
        assertThat(result.trace().getFirst()).satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_MEDABROAD_STAY);
            assertThat(line.kind()).isEqualTo(StepKind.INCOME);
            assertThat(line.limitedBy()).hasValueSatisfying(bound ->
                    assertThat(bound).contains("the extent permitted by the Reserve Bank of India"));
            assertThat(line.provenance().verificationLevel()).isEqualTo(year == 2025
                    ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheReserveBankPermittedItAll_treatmentIsNotSalary(int year) {
        // When — 4,00,000 spent, 5,00,000 permitted: the spending is the lesser
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "750000", List.of(claim("400000", "0", "500000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenIncomeBeforeTravelIsBelowTheThreshold_travelIsExcluded(int year) {
        // When — 7,50,000 does not exceed 8,00,000
        NodeResult result = run(year, NEW_TAX_REGIME, "750000", List.of(claim("0", "150000", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace().get(1).rule()).isEqualTo(RuleIds.PERQ_MEDABROAD_TRAVEL);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenIncomeBeforeTravelIsExactlyTheThreshold_travelIsExcluded(int year) {
        // When — 8,00,000 "does not exceed" 8,00,000
        NodeResult result = run(year, NEW_TAX_REGIME, "800000", List.of(claim("0", "150000", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenIncomeBeforeTravelExceedsTheThreshold_travelIsWhollySalary(int year) {
        // When — 8,00,001 exceeds 8,00,000
        NodeResult result = run(year, NEW_TAX_REGIME, "800001", List.of(claim("0", "150000", "0")));

        // Then — all 1,50,000, naming the condition not met
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("150000")));
        assertThat(result.trace().get(1).limitedBy()).hasValueSatisfying(bound ->
                assertThat(bound).startsWith("not excluded, not met"));
    }

    @Test
    void evaluate_whenBothLimbsAndTwoClaims_addsEveryPart() {
        // When — above the threshold: (6,00,000 − 5,00,000) + 1,50,000 + (2,00,000 − 2,00,000) + 50,000
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, "900000",
                List.of(claim("600000", "150000", "500000"), claim("200000", "50000", "200000")));

        // Then — 1,00,000 + 1,50,000 + 0 + 50,000 = 3,00,000, on four lines
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("300000")));
        assertThat(result.trace()).hasSize(4);
    }

    @Test
    void evaluate_whenNothingIsClaimed_isNilAndUntraced() {
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "900000", List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void evaluate_whenTheClaimNamesAnEmploymentNotEntered_isRefused() {
        // Given
        MedicalTreatmentAbroad claim = new MedicalTreatmentAbroad(
                3, Money.of("100000"), Money.ZERO, Money.of("100000"), 0);

        // When / Then
        assertThatThrownBy(() -> run(2026, NEW_TAX_REGIME, "900000", List.of(claim)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("employment 3");
    }
}
