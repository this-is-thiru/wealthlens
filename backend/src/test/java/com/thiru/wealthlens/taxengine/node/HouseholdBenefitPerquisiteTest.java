package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.BenefitArrangement;
import com.thiru.wealthlens.taxengine.input.EmployerChildEducation;
import com.thiru.wealthlens.taxengine.input.EmployerHouseholdBenefit;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseholdBenefitKind;
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
 * Household services, utilities, education and carriers' journeys (S4 r.15(4) Table III; Rules 1962 r.3(3)–(6);
 * D-120). A benefit the employer provides is salary only for a specified employee (monetary salary over ₹4,00,000);
 * one meeting the employee's obligation, for everyone (OQ-10). Education's limit: ₹3,000 / one thousand rupees per
 * child per month.
 */
class HouseholdBenefitPerquisiteTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final String SPECIFIED = "900000";
    private static final String NOT_SPECIFIED = "400000";

    private static EmployerHouseholdBenefit benefit(HouseholdBenefitKind kind, BenefitArrangement arrangement,
            String amount, String recovered) {
        return new EmployerHouseholdBenefit(0, kind, arrangement, Money.of(amount), Money.of(recovered), false);
    }

    private static EmployerChildEducation child(String costPerMonth) {
        return new EmployerChildEducation(0, true, Money.of(costPerMonth), 12, Money.ZERO);
    }

    private static NodeResult run(int year, String monetaryIncome, List<EmployerHouseholdBenefit> benefits,
            List<EmployerChildEducation> children) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())),
                        List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), benefits, children, List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(NEW_TAX_REGIME)
                .inputs(inputs)
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_ACCOM, List.of(Money.ZERO))))
                .given(NodeIds.SALARY_EXEMPTIONS, MoneyValue.of(Money.ZERO))
                .given(NodeIds.SALARY_MONETARYINCOME, MoneyValue.of(Money.of(monetaryIncome)))
                .run(new PerquisiteNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheEmployerProvidesAGardenerToASpecifiedEmployee_isTheSalaryLessThePayment(int year) {
        // When
        NodeResult result = run(year, SPECIFIED, List.of(benefit(HouseholdBenefitKind.SERVICES,
                BenefitArrangement.EMPLOYER_PROVIDED, "120000", "20000")), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel()).isEqualTo(year == 2025
                ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
    }

    @Test
    void evaluate_whenTheEmployerProvidesAGardenerToOneWhoIsNotSpecified_isNil() {
        // When — 4,00,000 does not exceed 4,00,000
        NodeResult result = run(2026, NOT_SPECIFIED, List.of(benefit(HouseholdBenefitKind.SERVICES,
                BenefitArrangement.EMPLOYER_PROVIDED, "120000", "20000")), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_SPECIFIED_EMPLOYEE);
    }

    @Test
    void evaluate_whenTheEmployerPaysTheEmployeesOwnServant_isSalaryForAnyone() {
        // When — s.17(1)(f): the employee's obligation, met
        NodeResult result = run(2026, NOT_SPECIFIED, List.of(benefit(HouseholdBenefitKind.SERVICES,
                BenefitArrangement.EMPLOYEE_OBLIGATION_MET, "120000", "20000")), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
    }

    @Test
    void evaluate_whenUtilitiesAreSupplied_isTheAmountPaidOrTheManufacturingCost() {
        // When — 36,000 to the agency; 10,000 units at ₹4.50 from the employer's plant
        NodeResult agency = run(2026, SPECIFIED, List.of(benefit(HouseholdBenefitKind.UTILITY_FROM_OUTSIDE_AGENCY,
                BenefitArrangement.EMPLOYER_PROVIDED, "36000", "0")), List.of());
        NodeResult ownPlant = run(2026, SPECIFIED, List.of(benefit(
                HouseholdBenefitKind.UTILITY_FROM_EMPLOYER_RESOURCES, BenefitArrangement.EMPLOYER_PROVIDED, "45000",
                "0")), List.of());

        // Then
        assertThat(agency.value()).isEqualTo(MoneyValue.of(Money.of("36000")));
        assertThat(ownPlant.value()).isEqualTo(MoneyValue.of(Money.of("45000")));
    }

    @Test
    void evaluate_whenEducationFeesArePaid_isTheExpenditureWithNoLimit() {
        // When
        NodeResult result = run(2026, SPECIFIED, List.of(benefit(HouseholdBenefitKind.EDUCATION_FEES,
                BenefitArrangement.EMPLOYEE_OBLIGATION_MET, "120000", "0")), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("120000")));
    }

    @Test
    void evaluate_whenAChildStudiesAtTheEmployersSchoolAtTwoThousandFiveHundred_isNilIn2026AndValuedIn2025() {
        // When — ₹2,500 a month: within ₹3,000; over one thousand rupees
        NodeResult within = run(2026, SPECIFIED, List.of(), List.of(child("2500")));
        NodeResult over = run(2025, SPECIFIED, List.of(), List.of(child("2500")));

        // Then — 2,500 × 12 = 30,000 in TY 2025-26
        assertThat(within.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(over.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheSchoolCostsMoreThanEitherLimit_isTheWholeValue(int year) {
        // When — ₹3,500 × 12, the whole, not the excess
        NodeResult result = run(year, SPECIFIED, List.of(), List.of(child("3500")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("42000")));
    }

    @Test
    void evaluate_whenTheSchoolCostsExactlyTheLimit_isNil() {
        // When — "exceeds ₹3,000": at 3,000, nil
        NodeResult result = run(2026, SPECIFIED, List.of(), List.of(child("3000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenACarriersEmployeeTravelsFree_isThePublicFare() {
        // When
        NodeResult result = run(2026, SPECIFIED, List.of(benefit(HouseholdBenefitKind.CARRIER_TRAVEL,
                BenefitArrangement.EMPLOYER_PROVIDED, "8000", "0")), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("8000")));
    }

    @Test
    void evaluate_whenAnAirlinesEmployeeTravelsFree_isRefused() {
        // Given
        EmployerHouseholdBenefit airline = new EmployerHouseholdBenefit(0, HouseholdBenefitKind.CARRIER_TRAVEL,
                BenefitArrangement.EMPLOYER_PROVIDED, Money.of("8000"), Money.ZERO, true);

        // When / Then
        assertThatThrownBy(() -> run(2026, SPECIFIED, List.of(airline), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("airline or the railways");
    }

    @Test
    void construct_whenTheEmployersOwnSupplyIsEnteredAsTheEmployeesObligation_isRefused() {
        // When / Then
        assertThatThrownBy(() -> benefit(HouseholdBenefitKind.UTILITY_FROM_EMPLOYER_RESOURCES,
                        BenefitArrangement.EMPLOYEE_OBLIGATION_MET, "45000", "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("employer's own supply");
    }
}
