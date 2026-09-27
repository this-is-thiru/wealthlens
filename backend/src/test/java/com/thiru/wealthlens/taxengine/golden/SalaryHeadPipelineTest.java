package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.input.DisabledTransportClaim;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
import com.thiru.wealthlens.taxengine.input.RetirementPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.node.ComponentPartitionNode;
import com.thiru.wealthlens.taxengine.node.ExemptionNode;
import com.thiru.wealthlens.taxengine.node.GrossSalaryNode;
import com.thiru.wealthlens.taxengine.node.RetirementNode;
import com.thiru.wealthlens.taxengine.node.SalaryBaseNode;
import com.thiru.wealthlens.taxengine.node.SalaryDeductionNode;
import com.thiru.wealthlens.taxengine.node.SalaryIncomeNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.definition.TransportDisability;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.PipelineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The salary head end to end on the shipped pack — its own components, no fixtures (D-094, D-096): the
 * partition, gross salary, the salary bases, the deductions and income under "Salaries", as a salaried person's entries reach them. Each figure is
 * added up by hand from the entries below and the statutory amounts, ₹75,000 (S2 s.19(1) Table Sl. 2(a)) and
 * ₹50,000 (Sl. 2(b)).
 *
 * <p>One employment: basic 6,00,000; dearness allowance 60,000; house rent allowance 2,40,000; professional tax
 * paid 2,500.
 */
class SalaryHeadPipelineTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final NodeRegistry SALARY_HEAD = NodeRegistry.of(List.of(
            new ComponentPartitionNode(), new GrossSalaryNode(), new SalaryBaseNode(), new ExemptionNode(),
            new RetirementNode(), new SalaryDeductionNode(), new SalaryIncomeNode()));
    private static final List<RuleId> BASES = List.of(
            RuleIds.SAL_SALARY_FOR_HRA,
            RuleIds.SAL_SALARY_FOR_GRATUITY,
            RuleIds.SAL_SALARY_FOR_EMPLOYER_PENSION,
            RuleIds.SAL_SALARY_FOR_EMPLOYEE_PENSION);

    private static PipelineRunner.Result run(int year, RegimeCode regime, boolean termsIncludeDearnessAllowance) {
        return run(year, regime, termsIncludeDearnessAllowance, List.of(), List.of(), List.of());
    }

    private static PipelineRunner.Result run(
            int year, RegimeCode regime, boolean termsIncludeDearnessAllowance, List<Tenancy> tenancies) {
        return run(year, regime, termsIncludeDearnessAllowance, tenancies, List.of(), List.of());
    }

    private static PipelineRunner.Result run(int year, RegimeCode regime, boolean termsIncludeDearnessAllowance,
            List<Tenancy> tenancies, List<Journey> journeys) {
        return run(year, regime, termsIncludeDearnessAllowance, tenancies, journeys, List.of());
    }

    private static PipelineRunner.Result run(int year, RegimeCode regime, boolean termsIncludeDearnessAllowance,
            List<Tenancy> tenancies, List<Journey> journeys, List<AllowanceUse> allowanceUses) {
        return run(year, regime, termsIncludeDearnessAllowance, tenancies, journeys, allowanceUses, Optional.empty());
    }

    private static PipelineRunner.Result run(int year, RegimeCode regime, boolean termsIncludeDearnessAllowance,
            List<Tenancy> tenancies, List<Journey> journeys, List<AllowanceUse> allowanceUses,
            Optional<DisabledTransportClaim> disabledTransport) {
        return run(year, regime, termsIncludeDearnessAllowance,
                new Entries(tenancies, journeys, allowanceUses, disabledTransport, List.of(), List.of()));
    }

    /** What a case enters beyond the base pay. */
    private record Entries(List<Tenancy> tenancies, List<Journey> journeys, List<AllowanceUse> allowanceUses,
            Optional<DisabledTransportClaim> disabledTransport, List<SalaryComponent> retirementPay,
            List<RetirementPayment> retirementPayments) {
    }

    private static PipelineRunner.Result run(int year, RegimeCode regime, boolean termsIncludeDearnessAllowance,
            Entries entries) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = RUNNER.pack(taxYear);
        RegimeSpec shipped = pack.regimes().get(regime);
        RegimeSpec salaryHead = new RegimeSpec(shipped.code(), shipped.displayName(), shipped.provision1961(),
                shipped.provision2025(), shipped.appliesTo(), shipped.rateTable(), shipped.disallowances(),
                shipped.eligibility(), shipped.election(), shipped.lifecycle(), shipped.isDefault(),
                new PipelineSpec(List.of(NodeIds.INPUTS_COMPONENTS, NodeIds.SALARY_GROSS, NodeIds.SALARY_BASE,
                        NodeIds.SALARY_EXEMPTIONS, NodeIds.SALARY_RETIREMENT, NodeIds.SALARY_DEDUCTIONS,
                        NodeIds.SALARY_INCOME)),
                shipped.behaviour(), shipped.provenance());
        List<SalaryComponent> pay = new java.util.ArrayList<>(List.of(
                new SalaryComponent(ComponentCodes.BASIC, Money.of("600000")),
                new SalaryComponent(ComponentCodes.DEARNESS_ALLOWANCE, Money.of("60000")),
                new SalaryComponent(ComponentCodes.HRA, Money.of("240000")),
                new SalaryComponent(ComponentCodes.PROFESSIONAL_TAX, Money.of("2500"))));
        if (!entries.journeys().isEmpty()) {
            pay.add(new SalaryComponent(ComponentCodes.LTA, Money.of("30000")));
        }
        if (!entries.allowanceUses().isEmpty()) {
            pay.add(new SalaryComponent(ComponentCodes.CONVEYANCE_ALLOWANCE, Money.of("20000")));
        }
        if (entries.disabledTransport().isPresent()) {
            pay.add(new SalaryComponent(ComponentCodes.TRANSPORT_ALLOWANCE_DISABLED, Money.of("50000")));
        }
        pay.addAll(entries.retirementPay());
        Employment employment = new Employment(pay, termsIncludeDearnessAllowance);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)), new EnteredFacts(List.of(employment), entries.tenancies(), entries.journeys(),
                        entries.allowanceUses(), entries.disabledTransport(), entries.retirementPayments()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return PipelineRunner.run(SALARY_HEAD, pack, salaryHead, inputs);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenPayAndProfessionalTaxAreEntered_grossSalaryIsThePayAlone(int year) {
        // When
        PipelineRunner.Result result = run(year, FIRST_SCHEDULE_RATES, true);

        // Then — 6,00,000 + 60,000 + 2,40,000 = 9,00,000; the 2,500 paid is not salary
        assertThat(result.valueOf(NodeIds.SALARY_GROSS)).isEqualTo(MoneyValue.of(Money.of("900000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenTheTermsProvideForDearnessAllowance_everyBaseIsBasicAndDearnessAllowance(int year) {
        // When
        SalaryBases bases = (SalaryBases) run(year, FIRST_SCHEDULE_RATES, true).valueOf(NodeIds.SALARY_BASE);

        // Then — 6,00,000 + 60,000 = 6,60,000; HRA is one of "all other allowances"
        BASES.forEach(rule -> assertThat(bases.of(rule, 0)).isEqualTo(Money.of("660000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenTheTermsAreSilent_everyBaseIsBasicAlone(int year) {
        // When
        SalaryBases bases = (SalaryBases) run(year, FIRST_SCHEDULE_RATES, false).valueOf(NodeIds.SALARY_BASE);

        // Then — 6,00,000
        BASES.forEach(rule -> assertThat(bases.of(rule, 0)).isEqualTo(Money.of("600000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenOutsideTheNewRegime_deductsProfessionalTaxAndFiftyThousand(int year) {
        // When
        PipelineRunner.Result result = run(year, FIRST_SCHEDULE_RATES, true);

        // Then — 2,500 + the lesser of 50,000 and 9,00,000 = 52,500
        assertThat(result.valueOf(NodeIds.SALARY_DEDUCTIONS)).isEqualTo(MoneyValue.of(Money.of("52500")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenInTheNewRegime_deductsSeventyFiveThousandAlone(int year) {
        // When
        PipelineRunner.Result result = run(year, NEW_TAX_REGIME, true);

        // Then — the lesser of 75,000 and 9,00,000; no professional tax (s.202(2)(a)(iv))
        assertThat(result.valueOf(NodeIds.SALARY_DEDUCTIONS)).isEqualTo(MoneyValue.of(Money.of("75000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenOutsideTheNewRegime_incomeUnderSalariesIsGrossLessBothDeductions(int year) {
        // When
        PipelineRunner.Result result = run(year, FIRST_SCHEDULE_RATES, true);

        // Then — 9,00,000 − 52,500 = 8,47,500
        assertThat(result.valueOf(NodeIds.SALARY_INCOME)).isEqualTo(MoneyValue.of(Money.of("847500")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenInTheNewRegime_incomeUnderSalariesIsGrossLessTheStandardDeduction(int year) {
        // When
        PipelineRunner.Result result = run(year, NEW_TAX_REGIME, true);

        // Then — 9,00,000 − 75,000 = 8,25,000
        assertThat(result.valueOf(NodeIds.SALARY_INCOME)).isEqualTo(MoneyValue.of(Money.of("825000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenARentedHomeIsEnteredOutsideTheNewRegime_incomeFallsByTheHraExemption(int year) {
        // Given — rent 1,20,000 for the whole year; Bengaluru is listed only from TY 2026-27 (H-2)
        Tenancy home = new Tenancy(
                DateRange.between(LocalDate.of(year, 4, 1), LocalDate.of(year + 1, 3, 31)),
                year == 2025 ? "ANY_OTHER_PLACE" : "BENGALURU", false, Money.of("120000"));

        // When
        PipelineRunner.Result result = run(year, FIRST_SCHEDULE_RATES, true, List.of(home));

        // Then — HRA base 6,60,000: least of 2,40,000; 1,20,000 − 66,000 = 54,000; 40% or 50% of 6,60,000
        // → 54,000; income 9,00,000 − 54,000 − 52,500 = 7,93,500
        assertThat(result.valueOf(NodeIds.SALARY_EXEMPTIONS)).isEqualTo(MoneyValue.of(Money.of("54000")));
        assertThat(result.valueOf(NodeIds.SALARY_INCOME)).isEqualTo(MoneyValue.of(Money.of("793500")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenARentedHomeIsEnteredInTheNewRegime_exemptsNothing(int year) {
        // Given
        Tenancy home = new Tenancy(
                DateRange.between(LocalDate.of(year, 4, 1), LocalDate.of(year + 1, 3, 31)),
                "ANY_OTHER_PLACE", false, Money.of("120000"));

        // When
        PipelineRunner.Result result = run(year, NEW_TAX_REGIME, true, List.of(home));

        // Then — 9,00,000 − 75,000 = 8,25,000, as without the home
        assertThat(result.valueOf(NodeIds.SALARY_INCOME)).isEqualTo(MoneyValue.of(Money.of("825000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenAJourneyOnLeaveIsClaimedOutsideTheNewRegime_incomeFallsByTheFareAdmitted(int year) {
        // Given — LTA 30,000 received; a rail journey in May: 18,000 spent, fare admitted 15,000
        Journey rail = new Journey(LocalDate.of(year, 5, 10), JourneyMode.RAIL_CONNECTED, Money.of("18000"),
                java.util.Optional.of(Money.of("15000")), java.util.Optional.empty());

        // When
        PipelineRunner.Result result = run(year, FIRST_SCHEDULE_RATES, true, List.of(), List.of(rail));

        // Then — exempt 15,000; income 9,30,000 − 15,000 − 52,500 = 8,62,500
        assertThat(result.valueOf(NodeIds.SALARY_EXEMPTIONS)).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(result.valueOf(NodeIds.SALARY_INCOME)).isEqualTo(MoneyValue.of(Money.of("862500")));
    }

    @org.junit.jupiter.api.Test
    void run_whenAConveyanceAllowanceIsSpentInTheNewRegime2026_incomeFallsByWhatWasSpent() {
        // Given — conveyance 20,000 paid, 15,000 spent; the new regime keeps conveyance (D-101)
        AllowanceUse conveyance = AllowanceUse.spent(ComponentCodes.CONVEYANCE_ALLOWANCE, Money.of("15000"));

        // When
        PipelineRunner.Result result = run(2026, NEW_TAX_REGIME, true, List.of(), List.of(), List.of(conveyance));

        // Then — gross 9,20,000; exempt 15,000; deduction 75,000; income 8,30,000
        assertThat(result.valueOf(NodeIds.SALARY_EXEMPTIONS)).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(result.valueOf(NodeIds.SALARY_INCOME)).isEqualTo(MoneyValue.of(Money.of("830000")));
    }

    @org.junit.jupiter.api.Test
    void run_whenABlindEmployeesTransportAllowanceInTheNewRegime2025_incomeFallsByTheMonthlyLimit() {
        // Given — transport 50,000 paid for all twelve months; r.2BB(3) keeps Table Sl. 11 under s.115BAC (D-102)
        DisabledTransportClaim blind = new DisabledTransportClaim(TransportDisability.BLIND, 12, Optional.empty(),
                Optional.empty());

        // When
        PipelineRunner.Result result =
                run(2025, NEW_TAX_REGIME, true, List.of(), List.of(), List.of(), Optional.of(blind));

        // Then — gross 9,50,000; exempt 3,200 × 12 = 38,400; deduction 75,000; income 8,36,600
        assertThat(result.valueOf(NodeIds.SALARY_GROSS)).isEqualTo(MoneyValue.of(Money.of("950000")));
        assertThat(result.valueOf(NodeIds.SALARY_EXEMPTIONS)).isEqualTo(MoneyValue.of(Money.of("38400")));
        assertThat(result.valueOf(NodeIds.SALARY_INCOME)).isEqualTo(MoneyValue.of(Money.of("836600")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenVoluntaryRetirementAndNoticePayArePaidInTheNewRegime_onlyTheRetirementLimitComesOff(int year) {
        // Given — VRS 8,00,000 and notice pay 1,00,000 on leaving (D-103)
        List<SalaryComponent> leaving = List.of(new SalaryComponent(ComponentCodes.VRS_COMPENSATION, Money.of("800000")),
                new SalaryComponent(ComponentCodes.NOTICE_PAY, Money.of("100000")));

        // When
        PipelineRunner.Result result = run(year, NEW_TAX_REGIME, true, new Entries(List.of(), List.of(), List.of(),
                Optional.empty(), leaving,
                List.of(RetirementPayment.voluntaryRetirement(ComponentCodes.VRS_COMPENSATION, false))));

        // Then — gross 9,00,000 + 8,00,000 + 1,00,000 = 18,00,000; retirement 5,00,000; standard deduction 75,000;
        // income 12,25,000; notice pay taxed in full
        assertThat(result.valueOf(NodeIds.SALARY_GROSS)).isEqualTo(MoneyValue.of(Money.of("1800000")));
        assertThat(result.valueOf(NodeIds.SALARY_RETIREMENT)).isEqualTo(MoneyValue.of(Money.of("500000")));
        assertThat(result.valueOf(NodeIds.SALARY_INCOME)).isEqualTo(MoneyValue.of(Money.of("1225000")));
    }
}
