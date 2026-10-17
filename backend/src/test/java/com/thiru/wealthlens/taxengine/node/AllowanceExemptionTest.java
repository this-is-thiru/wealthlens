package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.input.DisabledTransportClaim;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CommutingCity;
import com.thiru.wealthlens.taxengine.rules.definition.TransportDisability;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The allowance exemptions in {@link ExemptionNode} (Sch. III Table Sl. 12–13 with Rules 2026 r.280; s.10(14) with
 * Rules 1962 r.2BB; D-101). Each figure is worked by hand from the prescribed amounts: ₹3,000 and ₹9,000 per month
 * per child for TY 2026-27 (r.280(2) Sl. 7, 8), ₹100 and ₹300 for TY 2025-26 (r.2BB(2) Sl. 5, 6); for a disabled
 * employee's transport (D-102), ₹3,200 per month for TY 2025-26 (r.2BB(2) Sl. 11) and ₹15,000 metro or ₹8,000 other
 * cities, plus dearness allowance thereon, for TY 2026-27 (r.280(2) Sl. 10).
 */
class AllowanceExemptionTest {

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static NodeResult run(int year, RegimeCode regime, ComponentCode allowance, String received,
            AllowanceUse use) {
        return run(year, regime, allowance, received, List.of(use), Optional.empty(), 1);
    }

    private static NodeResult runTransport(int year, RegimeCode regime, String received,
            DisabledTransportClaim claim) {
        return run(year, regime, ComponentCodes.TRANSPORT_ALLOWANCE_DISABLED, received, List.of(), Optional.of(claim),
                1);
    }

    private static NodeResult run(int year, RegimeCode regime, ComponentCode allowance, String received,
            List<AllowanceUse> uses, Optional<DisabledTransportClaim> claim, int employments) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(Collections.nCopies(employments, new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())), List.of(),
                        List.of(), uses, claim, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of(HeadCode.SALARIES,
                        List.of(new Line(0, allowance, Money.of(received))))))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_HRA, List.of(Money.ZERO))))
                .run(new ExemptionNode());
    }

    @Test
    void evaluate_whenLessWasSpentOnUniformThanPaid_exemptsWhatWasSpent() {
        // When — 12,000 paid, 8,000 spent
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.UNIFORM_ALLOWANCE, "12000",
                AllowanceUse.spent(ComponentCodes.UNIFORM_ALLOWANCE, Money.of("8000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("8000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_ALW_UNIFORM);
            assertThat(line.kind()).isEqualTo(StepKind.EXEMPTION);
            assertThat(line.limitedBy()).contains("amount actually incurred");
        });
    }

    @Test
    void evaluate_whenConveyanceInTheNewRegime2026_isExemptAndVisiblyUnverified() {
        // When — 20,000 paid, 15,000 spent; r.280(3)'s list read into the new regime (D-101)
        NodeResult result = run(2026, NEW_TAX_REGIME, ComponentCodes.CONVEYANCE_ALLOWANCE, "20000",
                AllowanceUse.spent(ComponentCodes.CONVEYANCE_ALLOWANCE, Money.of("15000")));

        // Then — exempt, and the line says its version is unverified, so the computation is PROVISIONAL
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel()).isEqualTo(VerificationLevel.UNVERIFIED);
    }

    @Test
    void evaluate_whenConveyanceInTheNewRegime2025_isExemptOnClearWords() {
        // When — r.2BB(3) (G.S.R. 452(E)) names (1)(c) for an assessee taxed under s.115BAC(1A)
        NodeResult result = run(2025, NEW_TAX_REGIME, ComponentCodes.CONVEYANCE_ALLOWANCE, "20000",
                AllowanceUse.spent(ComponentCodes.CONVEYANCE_ALLOWANCE, Money.of("15000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel())
                .isEqualTo(VerificationLevel.SOURCE_CHECKED);
    }

    @Test
    void evaluate_whenUniformInTheNewRegime_exemptsNothingAndSaysWhy() {
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, ComponentCodes.UNIFORM_ALLOWANCE, "12000",
                AllowanceUse.spent(ComponentCodes.UNIFORM_ALLOWANCE, Money.of("8000")));

        // Then — s.202(2)(a)(ii): not prescribed by r.280(3), so a nil line for what was received (D-166)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_ALW_UNIFORM);
            assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("12000")));
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
            assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).startsWith("disallowed in the NEW_TAX_REGIME regime: ")
                        .endsWith("(" + RuleIds.REG_NEW_TAX_REGIME_NO_EXEMPTION + ")"));
        });
    }

    @Test
    void evaluate_whenChildrenEducationFor2ChildrenAllYear_exemptsThePrescribedLimit() {
        // When — 3,000 × 2 × 12 = 72,000, against 90,000 paid
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, "90000",
                AllowanceUse.forChildren(ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, 2, 12));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("72000")));
        assertThat(result.trace().getFirst().limitedBy()).contains("prescribed limit");
    }

    @Test
    void evaluate_whenChildrenEducationForThreeChildren_countsTwo() {
        // When — the third child earns nothing: still 3,000 × 2 × 12 = 72,000
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, "120000",
                AllowanceUse.forChildren(ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, 3, 12));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("72000")));
    }

    @Test
    void evaluate_whenHostelForOneChildSixMonths_exemptsSixMonthsOfTheLimit() {
        // When — 9,000 × 1 × 6 = 54,000, against 60,000 paid
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.HOSTEL_ALLOWANCE, "60000",
                AllowanceUse.forChildren(ComponentCodes.HOSTEL_ALLOWANCE, 1, 6));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("54000")));
    }

    @Test
    void evaluate_whenChildrenEducationIn2025_usesTheRule2bbAmount() {
        // When — 100 × 2 × 12 = 2,400, against 30,000 paid
        NodeResult result = run(2025, FIRST_SCHEDULE_RATES, ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, "30000",
                AllowanceUse.forChildren(ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, 2, 12));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("2400")));
    }

    @Test
    void evaluate_whenAPrescribedAllowanceIsEnteredWithOnlyAnAmount_isRefused() {
        // When / Then — Sl. 13 is per child per month; an amount spent says nothing about either
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE,
                        "90000", AllowanceUse.spent(ComponentCodes.CHILDREN_EDUCATION_ALLOWANCE, Money.of("50000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("children");
    }

    @Test
    void evaluate_whenAUseNamesAComponentWithNoAllowanceExemption_isRefused() {
        // When / Then — basic pay has no allowance exemption to claim
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.BASIC, "600000",
                        AllowanceUse.spent(ComponentCodes.BASIC, Money.of("1000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BASIC");
    }

    @Test
    void evaluate_whenBlindAllOf2025_exemptsThreeThousandTwoHundredAMonth() {
        // When — 3,200 × 12 = 38,400, against 50,000 paid
        NodeResult result = runTransport(2025, FIRST_SCHEDULE_RATES, "50000", new DisabledTransportClaim(
                TransportDisability.BLIND, 12, Optional.empty(), Optional.empty()));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("38400")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_ALW_TRANSPORT_DISABLED);
            assertThat(line.kind()).isEqualTo(StepKind.EXEMPTION);
            assertThat(line.limitedBy()).contains("limit per month");
            assertThat(line.provenance().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
        });
    }

    @Test
    void evaluate_whenUpperExtremitiesIn2025_givesANilLineNamingWhy() {
        // When — r.2BB(2) Sl. 11 names a disability of the lower extremities only
        NodeResult result = runTransport(2025, FIRST_SCHEDULE_RATES, "50000", new DisabledTransportClaim(
                TransportDisability.ORTHOPAEDIC_UPPER_EXTREMITIES, 12, Optional.empty(), Optional.empty()));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> assertThat(line.limitedBy())
                .hasValueSatisfying(why -> assertThat(why).contains("ORTHOPAEDIC_UPPER_EXTREMITIES")));
    }

    @Test
    void evaluate_whenMetroWithDearnessAllowanceIn2026_isCappedByWhatWasPaidAndVisiblyUnverified() {
        // When — (15,000 + 1,500) × 12 = 1,98,000, against 1,80,000 paid
        NodeResult result = runTransport(2026, FIRST_SCHEDULE_RATES, "180000", new DisabledTransportClaim(
                TransportDisability.ORTHOPAEDIC_UPPER_EXTREMITIES, 12, Optional.of(CommutingCity.METRO_CITY),
                Optional.of(Money.of("1500"))));

        // Then — OQ-7: the terms are taken as stated, so the version is unverified and the figure PROVISIONAL
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("180000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.limitedBy()).contains("allowance received");
            assertThat(line.provenance().verificationLevel()).isEqualTo(VerificationLevel.UNVERIFIED);
        });
    }

    @Test
    void evaluate_whenOtherCityForSixMonthsIn2026_exemptsTheOtherCitiesAmount() {
        // When — 8,000 × 6 = 48,000, against 60,000 paid
        NodeResult result = runTransport(2026, FIRST_SCHEDULE_RATES, "60000", new DisabledTransportClaim(
                TransportDisability.DEAF_AND_DUMB, 6, Optional.of(CommutingCity.OTHER_CITY), Optional.empty()));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("48000")));
    }

    @Test
    void evaluate_whenTransportInTheNewRegime_isStillExempt() {
        // When — r.2BB(3) (G.S.R. 452(E)) names Table Sl. 11 for an assessee taxed under s.115BAC(1A)
        NodeResult result = runTransport(2025, NEW_TAX_REGIME, "50000", new DisabledTransportClaim(
                TransportDisability.BLIND, 12, Optional.empty(), Optional.empty()));

        // Then — 3,200 × 12 = 38,400
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("38400")));
    }

    @Test
    void evaluate_whenTheCityIsNotStatedIn2026_isRefused() {
        // When / Then — the limit depends on the city class, which the Rules leave to be stated (OQ-7)
        assertThatThrownBy(() -> runTransport(2026, FIRST_SCHEDULE_RATES, "60000", new DisabledTransportClaim(
                        TransportDisability.BLIND, 12, Optional.empty(), Optional.empty())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metro");
    }

    @Test
    void evaluate_whenTransportIsEnteredAsAnAllowanceUse_isRefusedNamingTheClaim() {
        // When / Then — its limit turns on the disability, not on an amount spent
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, ComponentCodes.TRANSPORT_ALLOWANCE_DISABLED,
                        "60000", AllowanceUse.spent(ComponentCodes.TRANSPORT_ALLOWANCE_DISABLED, Money.of("1000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled transport claim");
    }

    @Test
    void evaluate_whenTwoEmploymentsAndATransportClaim_isRefused() {
        // When / Then — as for every exemption: the months each employer paid cannot be told apart
        assertThatThrownBy(() -> run(2025, FIRST_SCHEDULE_RATES, ComponentCodes.TRANSPORT_ALLOWANCE_DISABLED,
                        "50000", List.of(), Optional.of(new DisabledTransportClaim(TransportDisability.BLIND, 12,
                                Optional.empty(), Optional.empty())), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("employment");
    }
}
