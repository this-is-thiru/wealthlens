package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.LifeInsurancePolicy;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The s.123 family and the pension scheme under Chapter VIII (S2 ss.122–124, Schedule XV; D-130, D-132). Figures by
 * hand from {@code DR 01} R15's worked example (its life insurance premium left out — clause (a) is held), {@code DR 08}
 * T5-3 and T4-1/T4-2's facts.
 */
class ChapterEightNodeTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");

    /** What a case enters, and the figures the earlier nodes would have produced for it. */
    private record Case(int year, RegimeCode regime, EmployerKind employerKind, List<ComponentSet.Line> payLines,
            List<QualifyingPayment> payments, List<EmployerFundContribution> employerContributions, Money salary,
            Money grossTotal) {

        Case {
            payLines = List.copyOf(payLines);
            payments = List.copyOf(payments);
            employerContributions = List.copyOf(employerContributions);
        }
    }

    private static ComponentSet.Line pay(ComponentCode code, String amount) {
        return new ComponentSet.Line(0, code, Money.of(amount));
    }

    private static QualifyingPayment paid(ComponentCode code, String amount) {
        return new QualifyingPayment(code, Money.of(amount));
    }

    private static EmployerFundContribution employerPension(int employment, String amount) {
        return new EmployerFundContribution(employment, EmployerFund.NOTIFIED_PENSION_SCHEME, Money.of(amount));
    }

    private static NodeResult run(Case entered) {
        return run(entered, List.of());
    }

    private static NodeResult run(Case entered, List<LifeInsurancePolicy> policies) {
        TaxYearId taxYear = TaxYearId.of(entered.year());
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        List<Employment> employments = Collections.nCopies(2,
                new Employment(List.of(), false, entered.employerKind(), false, Optional.empty()));
        EngineInputs inputs = new EngineInputs(taxYear, entered.regime(),
                TemporalContext.forTaxYear(taxYear, LocalDate.of(entered.year(), 9, 21)),
                new EnteredFacts(employments, List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), entered.employerContributions(), Optional.empty(), List.of(),
                        entered.payments(), policies, List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        List<Money> perEmployment = List.of(entered.salary(), Money.ZERO);
        return NodeHarness.with(pack).regime(entered.regime()).inputs(inputs)
                .given(NodeIds.GTI, entered.grossTotal())
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(entered.payLines().isEmpty()
                        ? Map.of() : Map.of(HeadCode.SALARIES, new ArrayList<>(entered.payLines()))))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(
                        RuleIds.SAL_SALARY_FOR_EMPLOYEE_PENSION, perEmployment,
                        RuleIds.SAL_SALARY_FOR_EMPLOYER_PENSION, perEmployment)))
                .run(new ChapterEightNode());
    }

    /** R15: salary 10,00,000; RPF 1,20,000 and NPS 1,50,000 withheld; tuition 60,000 paid. */
    private static Case r15(RegimeCode regime, String grossTotal) {
        return new Case(2026, regime, EmployerKind.OTHER,
                List.of(pay(ComponentCodes.EMPLOYEE_PF, "120000"), pay(ComponentCodes.EMPLOYEE_NPS, "150000")),
                List.of(paid(ComponentCodes.TUITION_FEES, "60000")), List.of(), Money.of("1000000"),
                Money.of(grossTotal));
    }

    private static Case employerOnly(RegimeCode regime, EmployerKind kind) {
        return new Case(2026, regime, kind, List.of(), List.of(), List.of(employerPension(0, "336000")),
                Money.of("2400000"), Money.of("2474000"));
    }

    @Test
    void evaluate_whenR15sExampleIsEntered_allowsTheCeilingThenTheNpsLeftOver() {
        // When
        NodeResult result = run(r15(OLD, "1000000"));

        // Then — NPS sub-capped at 10% × 10,00,000 = 1,00,000; 1,20,000 + 60,000 + 1,00,000 = 2,80,000 → 1,50,000;
        // the 50,000 left → s.124(3) 50,000; 2,00,000 in all
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("200000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.DED_SCHEDULE_XV, RuleIds.DED_NPS_OWN);
        assertThat(result.trace().getFirst().in()).isEqualTo(MoneyValue.of(Money.of("280000")));
    }

    @Test
    void evaluate_whenT5ThreesNpsIsEntered_allowsNinetyThousand() {
        // Given — T5-3: salary 4,00,000, NPS 1,50,000, nothing else
        Case t53 = new Case(2026, OLD, EmployerKind.OTHER, List.of(pay(ComponentCodes.EMPLOYEE_NPS, "150000")),
                List.of(), List.of(), Money.of("400000"), Money.of("400000"));

        // When
        NodeResult result = run(t53);

        // Then — 10% × 4,00,000 = 40,000 under s.123; residue 1,10,000 → 50,000 under s.124(3)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("90000")));
    }

    @Test
    void evaluate_whenAPrivateEmployerContributesInTheOldRegime_allowsTenPerCent() {
        // When — T4-2: 3,36,000 on salary 24,00,000
        NodeResult result = run(employerOnly(OLD, EmployerKind.OTHER));

        // Then — 10% × 24,00,000 = 2,40,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("240000")));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.DED_NPS_EMPLOYER_OTHER));
    }

    @Test
    void evaluate_whenAPrivateEmployerContributesInTheNewRegime_allowsFourteenPerCent() {
        // When — T4-1
        NodeResult result = run(employerOnly(NEW, EmployerKind.OTHER));

        // Then — s.124(2): 14% × 24,00,000 = 3,36,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("336000")));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.DED_NPS_EMPLOYER_NEW_TAX_REGIME));
    }

    @Test
    void evaluate_whenTheGovernmentContributesInTheOldRegime_allowsFourteenPerCent() {
        // When
        NodeResult result = run(employerOnly(OLD, EmployerKind.GOVERNMENT));

        // Then — s.124(1)(a): 14% × 24,00,000 = 3,36,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("336000")));
    }

    @Test
    void evaluate_whenSchedulePaymentsAreEnteredInTheNewRegime_givesANilLineNamingTheRegime() {
        // When
        NodeResult result = run(r15(NEW, "1000000"));

        // Then — s.202(2)(a)(xii) keeps neither s.123 nor s.124(3)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.DED_SCHEDULE_XV);
            assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).startsWith("disallowed in the NEW_TAX_REGIME regime: ")
                        .endsWith("(" + RuleIds.REG_NEW_TAX_REGIME_NO_EXEMPTION + ")"));
        });
    }

    @Test
    void evaluate_whenTheDeductionsExceedGrossTotalIncome_capsThemAtIt() {
        // When — R15's 2,00,000 against gross total income of 1,00,000
        NodeResult result = run(r15(OLD, "100000"));

        // Then — s.122(2)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
        assertThat(result.trace().getLast().rule()).isEqualTo(RuleIds.DED_WITHIN_GROSS_TOTAL);
    }

    @Test
    void evaluate_whenNothingIsEntered_isNilAndUntraced() {
        // When
        NodeResult result = run(new Case(2026, OLD, EmployerKind.OTHER, List.of(), List.of(), List.of(),
                Money.of("1000000"), Money.of("1000000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void evaluate_whenTaxYear2025InvestmentsExceedTheCeiling_allowsOneLakhFiftyThousand() {
        // When — 1961 s.80CCE: PF 1,00,000 + ELSS 40,000 + tuition 60,000 = 2,00,000 within 1,50,000 (D-144)
        NodeResult result = run(new Case(2025, OLD, EmployerKind.OTHER,
                List.of(pay(ComponentCodes.EMPLOYEE_PF, "100000")),
                List.of(paid(ComponentCodes.MUTUAL_FUND_UNITS, "40000"), paid(ComponentCodes.TUITION_FEES, "60000")),
                List.of(), Money.of("1000000"), Money.of("1000000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("150000")));
    }

    @Test
    void evaluate_whenTaxYear2025NpsAndAMinorsDepositAreEntered_allowsTheSubCapThenFiftyThousand() {
        // When — s.80CCD(1)(a) 10% of 10,00,000 = 1,00,000 in s.80CCE; residue 30,000 + minor 30,000 under (1B), 50,000
        NodeResult result = run(new Case(2025, OLD, EmployerKind.OTHER,
                List.of(pay(ComponentCodes.EMPLOYEE_NPS, "130000")),
                List.of(paid(ComponentCodes.NPS_MINOR_DEPOSIT, "30000")),
                List.of(), Money.of("1000000"), Money.of("1000000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("150000")));
    }

    @Test
    void evaluate_whenTaxYear2025EmployerPensionIsEntered_allowsSection80CcdTwoByEmployerAndRegime() {
        // When — 1,20,000 on salary 10,00,000: another employer 10% old, 14% new; a Government 14% (M2, M8, M15)
        NodeResult otherOld = run(new Case(2025, OLD, EmployerKind.OTHER, List.of(), List.of(),
                List.of(employerPension(0, "120000")), Money.of("1000000"), Money.of("1000000")));
        NodeResult otherNew = run(new Case(2025, NEW, EmployerKind.OTHER, List.of(), List.of(),
                List.of(employerPension(0, "120000")), Money.of("1000000"), Money.of("1000000")));
        NodeResult government = run(new Case(2025, OLD, EmployerKind.GOVERNMENT, List.of(), List.of(),
                List.of(employerPension(0, "120000")), Money.of("1000000"), Money.of("1000000")));

        // Then
        assertThat(otherOld.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
        assertThat(otherNew.value()).isEqualTo(MoneyValue.of(Money.of("120000")));
        assertThat(government.value()).isEqualTo(MoneyValue.of(Money.of("120000")));
    }

    @Test
    void evaluate_whenATaxYear2025PolicyIssuedIn2011IsEntered_countsTwentyPerCentOfTheSumAssured() {
        // When — 1961 s.80C(3): issued on or before 31-3-2012, 20% of 1,00,000 = 20,000 of the 30,000 premium
        NodeResult result = run(new Case(2025, OLD, EmployerKind.OTHER, List.of(), List.of(), List.of(),
                        Money.of("1000000"), Money.of("1000000")),
                List.of(new LifeInsurancePolicy(Money.of("30000"), Money.of("100000"), LocalDate.of(2011, 6, 1), false)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("20000")));
    }

    @Test
    void evaluate_whenTwoEmployersContributeToThePension_isRefused() {
        // When / Then — OQ-15
        assertThatThrownBy(() -> run(new Case(2026, OLD, EmployerKind.OTHER, List.of(), List.of(),
                        List.of(employerPension(0, "100000"), employerPension(1, "100000")), Money.of("1000000"),
                        Money.of("1000000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-15");
    }

    @Test
    void evaluate_whenAPayCodeIsEnteredAsAQualifyingPayment_isRefused() {
        // When / Then — only a QUALIFYING_PAYMENT code is entered outside pay
        assertThatThrownBy(() -> run(new Case(2026, OLD, EmployerKind.OTHER, List.of(),
                        List.of(paid(ComponentCodes.EMPLOYEE_PF, "10000")), List.of(), Money.of("1000000"),
                        Money.of("1000000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a qualifying payment");
    }

    /** What s.123 allows for one policy's premium of 50,000 and nothing else, in the old regime. */
    private static NodeResult onePolicy(String sumAssured, LocalDate issuedOn, boolean coversDisabilityOrDisease) {
        return run(new Case(2026, OLD, EmployerKind.OTHER, List.of(), List.of(), List.of(), Money.of("1000000"),
                Money.of("1000000")), List.of(new LifeInsurancePolicy(Money.of("50000"), Money.of(sumAssured), issuedOn,
                coversDisabilityOrDisease)));
    }

    @Test
    void evaluate_whenAPolicyIssuedAfterApril2012IsPaidFor_countsTenPerCentOfTheSumAssured() {
        // When
        NodeResult result = onePolicy("400000", LocalDate.of(2015, 6, 1), false);

        // Then — para 2(1)(b): 10% × 4,00,000 = 40,000 of the 50,000 premium
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
        assertThat(result.trace().getFirst()).satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.DED_PREMIUM_SHARE);
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("40000")));
        });
    }

    @Test
    void evaluate_whenAPolicyIssuedBeforeApril2012IsPaidFor_countsThePremiumWithinTwentyPerCent() {
        // When — para 2(1)(a): 20% × 4,00,000 = 80,000, above the 50,000 premium
        NodeResult result = onePolicy("400000", LocalDate.of(2011, 6, 1), false);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("50000")));
    }

    @Test
    void evaluate_whenAPolicyFrom2013CoversADisability_countsFifteenPerCent() {
        // When — para 2(1)(c): 15% × 3,00,000 = 45,000
        NodeResult result = onePolicy("300000", LocalDate.of(2015, 6, 1), true);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("45000")));
    }

    @Test
    void evaluate_whenAPolicyFrom2012CoversADisability_stillCountsTenPerCent() {
        // When — issued 1-6-2012, before para 2(1)(c)'s 1-4-2013: 10% × 4,00,000
        NodeResult result = onePolicy("400000", LocalDate.of(2012, 6, 1), true);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
    }

    @Test
    void evaluate_whenAPremiumIsEnteredWithoutItsPolicy_isRefused() {
        // When / Then — para 2 needs the policy
        assertThatThrownBy(() -> run(new Case(2026, OLD, EmployerKind.OTHER, List.of(),
                        List.of(paid(ComponentCodes.LIFE_INSURANCE_PREMIUM, "50000")), List.of(), Money.of("1000000"),
                        Money.of("1000000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entered with its policy");
    }

    @Test
    void evaluate_whenAPolicyIsPaidForInTheNewRegime_givesANilLineForTheWholePremium() {
        // When
        NodeResult result = run(new Case(2026, NEW, EmployerKind.OTHER, List.of(), List.of(), List.of(),
                Money.of("1000000"), Money.of("1000000")), List.of(new LifeInsurancePolicy(Money.of("50000"),
                Money.of("400000"), LocalDate.of(2015, 6, 1), false)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.DED_SCHEDULE_XV);
            assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("50000")));
        });
    }

    /** Schedule XV para 1(c): a Government employee, the given pay lines and ₹1,50,000 deducted for an annuity. */
    private static Case governmentAnnuity(int year, EmployerKind kind, ComponentSet.Line... pay) {
        List<ComponentSet.Line> lines = new ArrayList<>(List.of(pay));
        lines.add(pay(ComponentCodes.GOVERNMENT_DEFERRED_ANNUITY, "150000"));
        return new Case(year, OLD, kind, lines, List.of(), List.of(), Money.of("600000"), Money.of("700000"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAGovernmentSalaryDeductionBuysADeferredAnnuity_countsTwentyPerCentOfThePay(int year) {
        // When — basic 6,00,000; 1,50,000 deducted
        NodeResult result = run(governmentAnnuity(year, EmployerKind.GOVERNMENT, pay(ComponentCodes.BASIC, "600000")));

        // Then — Schedule XV para 1(c) "20 % of salary"; 1961 s.80C(2)(iii) "one-fifth": 1,20,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("120000")));
    }

    @Test
    void evaluate_whenTheGovernmentPayIncludesAnAllowance_measuresTheAnnuityOnAllThePay() {
        // When — basic 6,00,000 and HRA 1,00,000: the salary payable, not the narrow basic-and-DA base (OQ-16)
        NodeResult result = run(governmentAnnuity(2026, EmployerKind.GOVERNMENT, pay(ComponentCodes.BASIC, "600000"),
                pay(ComponentCodes.HRA, "100000")));

        // Then — 20 % of 7,00,000 = 1,40,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("140000")));
    }

    @Test
    void evaluate_whenTheAnnuityDeductionIsFromAPrivateSalary_isRefused() {
        // When / Then — para 1(c) is salary "payable by or on behalf of the Government"
        assertThatThrownBy(() -> run(governmentAnnuity(2026, EmployerKind.OTHER, pay(ComponentCodes.BASIC, "600000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payable by the Government");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenUnitLinkedPlansAndAnAnnuityPlanArePaid_countsThemInFull(int year) {
        // Given — (j) 40,000, (k) 30,000, (l) 50,000; then 60,000 tuition as well
        List<QualifyingPayment> plans = List.of(paid(ComponentCodes.UNIT_LINKED_INSURANCE_PLAN, "40000"),
                paid(ComponentCodes.MUTUAL_FUND_UNIT_LINKED_PLAN, "30000"), paid(ComponentCodes.ANNUITY_PLAN, "50000"));
        List<QualifyingPayment> withTuition = new ArrayList<>(plans);
        withTuition.add(paid(ComponentCodes.TUITION_FEES, "60000"));

        // When
        NodeResult alone = run(new Case(year, OLD, EmployerKind.OTHER, List.of(), plans, List.of(), Money.of("1000000"),
                Money.of("1000000")));
        NodeResult capped = run(new Case(year, OLD, EmployerKind.OTHER, List.of(), withTuition, List.of(),
                Money.of("1000000"), Money.of("1000000")));

        // Then — para 2 reaches (a) alone (OQ-17): 1,20,000 counted whole; with tuition 1,80,000 → the ceiling 1,50,000
        assertThat(alone.value()).isEqualTo(MoneyValue.of(Money.of("120000")));
        assertThat(capped.value()).isEqualTo(MoneyValue.of(Money.of("150000")));
    }
}
