package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.HousingLoan;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.input.LoanPurpose;
import com.thiru.wealthlens.taxengine.input.LoanUse;
import com.thiru.wealthlens.taxengine.input.PurposeLoanInterest;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.RentDeclaration;
import com.thiru.wealthlens.taxengine.input.RetirementEntry;
import com.thiru.wealthlens.taxengine.input.RetirementPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * F24 at the level the PRD states it (W-119a C1a): for each of {@code DR 12} C3.1's thirteen checks, a whole computation
 * through the shipped pipeline never counts a claim the law forbids on the person's facts. A claim the law keeps out
 * gives the same total income as the same facts without it, and its line says why; a claim whose facts contradict each
 * other is refused in words (C2a). No figure is typed by hand: each expected total is the run without the claim.
 *
 * <p>The facts are the node tests' own, cited per case. TY 2026-27; basic pay 10,00,000 unless a case says otherwise.
 */
class StatutoryValidityTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final Rate WHOLE = Rate.percent("100");
    private static final DateRange WHOLE_YEAR = DateRange.between(TY_2026_27.startsOn(), TY_2026_27.endsOn());
    private static final ComponentCode TUITION_FEES = new ComponentCode("TUITION_FEES");

    /** {@code HousePropertyInterestTest}'s dates: borrowed in TY 2022-23, acquired in TY 2024-25. */
    private static final LocalDate BORROWED = LocalDate.of(2022, 5, 1);
    private static final LocalDate ACQUIRED = LocalDate.of(2024, 6, 1);

    private static EngineInputs inputs(RegimeCode regime, EnteredFacts entered) {
        return inputs(regime, entered, NodeHarness.plainTaxpayer().ledgers());
    }

    private static EngineInputs inputs(RegimeCode regime, EnteredFacts entered, LedgerSnapshot ledgers) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, regime, plain.temporal(), entered, plain.assessee(), plain.family(), ledgers,
                plain.elections(), plain.capitalGains(), plain.filing());
    }

    private static EnteredFacts pay(String basic, SalaryComponent... others) {
        List<SalaryComponent> components = new java.util.ArrayList<>(
                List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of(basic))));
        components.addAll(List.of(others));
        return EnteredFacts.none().withEmployments(List.of(new Employment(components, false, EmployerKind.OTHER, false,
                Optional.empty())));
    }

    private static SalaryComponent component(ComponentCode code, String rupees) {
        return new SalaryComponent(code, Money.of(rupees));
    }

    private static PipelineRunner.Result run(EngineInputs inputs) {
        return RUNNER.run(inputs).result();
    }

    private static Money totalIncome(EngineInputs inputs) {
        return ((MoneyValue) run(inputs).valueOf(NodeIds.TOTALINCOME)).amount();
    }

    private static List<TraceLine> lines(EngineInputs inputs, RuleId rule) {
        return run(inputs).trace().stream().filter(line -> line.rule().equals(rule)).toList();
    }

    private static Money out(TraceLine line) {
        return ((MoneyValue) line.out()).amount();
    }

    /** The claim's line is nil and gives its reason, and the total is the total without the claim. */
    private static void keptOut(EngineInputs withClaim, EngineInputs withoutClaim, RuleId rule, ReasonCode reason) {
        assertThat(totalIncome(withClaim)).isEqualTo(totalIncome(withoutClaim));
        assertThat(lines(withClaim, rule)).isNotEmpty().allSatisfy(line -> {
            assertThat(out(line)).isEqualTo(Money.ZERO);
            assertThat(line.reason()).hasValueSatisfying(why -> assertThat(why.code()).isEqualTo(reason));
        });
    }

    private static HouseProperty ownResidence(HousingLoan... loans) {
        return new HouseProperty(HouseOccupancy.OWN_RESIDENCE, Money.of("480000"), Money.ZERO, Money.ZERO, false,
                Money.ZERO, WHOLE, Money.ZERO, Optional.of(ACQUIRED), List.of(loans));
    }

    @Test
    void row01_whenTheInvestmentsExceedTheCeiling_countsTheCeiling() {
        // Given — Sch. XV's ceiling: 2,00,000 of tuition fees against the same facts at the ceiling's own amount
        EngineInputs above = inputs(OLD, pay("1000000").withQualifyingPayments(
                List.of(new QualifyingPayment(TUITION_FEES, Money.of("200000")))));
        EngineInputs atCeiling = inputs(OLD, pay("1000000").withQualifyingPayments(
                List.of(new QualifyingPayment(TUITION_FEES, Money.of("150000")))));

        // When / Then
        assertThat(totalIncome(above)).isEqualTo(totalIncome(atCeiling));
        assertThat(lines(above, RuleIds.DED_SCHEDULE_XV)).singleElement()
                .satisfies(line -> assertThat(out(line)).isEqualTo(out(lines(atCeiling, RuleIds.DED_SCHEDULE_XV).getFirst())));
    }

    @Test
    void row02_whenHouseRentAllowanceIsClaimedOnAnOwnedHome_exemptsNothing() {
        // Given — ExemptionNodeTest: Sch. III Table Sl. 11(c)
        EnteredFacts hra = pay("1000000", component(ComponentCodes.HRA, "400000"));
        EngineInputs owned = inputs(OLD, hra.withTenancies(
                List.of(new Tenancy(WHOLE_YEAR, "BENGALURU", true, Money.of("360000")))));

        // When / Then
        keptOut(owned, inputs(OLD, hra), RuleIds.SAL_HOUSE_RENT_ALLOWANCE, ReasonCodes.HRA_NOT_RENTED);
    }

    @Test
    void row03_whenRentIsDeductedWhileHouseRentAllowanceIsReceived_deductsNothing() {
        // Given — RentPaidDeductionTest: s.134(4)(b), any allowance however small
        EnteredFacts renting = pay("1000000", component(ComponentCodes.HRA, "1")).withTenancies(
                List.of(new Tenancy(WHOLE_YEAR, "ANY_OTHER_PLACE", false, Money.of("180000"))));
        EngineInputs declared = inputs(OLD, renting.withRentDeclaration(Optional.of(new RentDeclaration(12, false, false))));

        // When / Then
        keptOut(declared, inputs(OLD, renting), RuleIds.DED_RENT_PAID, ReasonCodes.RENT_RECEIVES_HRA);
    }

    @Test
    void row04_whenAHouseWithRentIsEnteredAsAnOwnResidence_isRefused() {
        // When / Then — HousePropertyNodeTest: S2 s.21(7)(b), let at any time is let
        assertThatThrownBy(() -> new HouseProperty(HouseOccupancy.OWN_RESIDENCE, Money.of("300000"), Money.of("10000"),
                Money.ZERO, false, Money.ZERO, WHOLE, Money.ZERO, Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enter it as LET");
    }

    @Test
    void row05_whenThreeHousesAreOwnResidences_isRefused() {
        // Given — HousePropertyNodeTest: S2 s.21(7)(a), two
        HouseProperty home = ownResidence();
        EngineInputs three = inputs(OLD, pay("1000000").withHouseProperties(List.of(home, home, home)));

        // When / Then
        assertThatThrownBy(() -> run(three)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only 2");
    }

    @Test
    void row06_whenOwnResidenceInterestIsClaimedInTheDefaultRegime_deductsNone() {
        // Given — HousePropertyInterestTest: s.202(2)(a)(v) removes the year's interest; no pre-construction instalment
        // here, which TY 2026-27's default regime still allows
        HousingLoan loan = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, Money.of("150000"), Money.ZERO,
                Money.ZERO, true, false);
        EngineInputs claimed = inputs(NEW, pay("1000000").withHouseProperties(List.of(ownResidence(loan))));
        EngineInputs unclaimed = inputs(NEW, pay("1000000").withHouseProperties(List.of(ownResidence())));

        // When / Then
        keptOut(claimed, unclaimed, RuleIds.HP_OWN_RESIDENCE_INTEREST, ReasonCodes.REGIME_DISALLOWS);
    }

    @Test
    void row07_whenTaxOnEmploymentIsClaimedInTheDefaultRegime_deductsNone() {
        // Given — SalaryDeductionNodeTest: 2,500, which the regime's set removes (D-165)
        EngineInputs claimed = inputs(NEW, pay("1000000", component(ComponentCodes.PROFESSIONAL_TAX, "2500")));

        // When / Then
        keptOut(claimed, inputs(NEW, pay("1000000")), RuleIds.SAL_TAX_ON_EMPLOYMENT, ReasonCodes.REGIME_DISALLOWS);
    }

    @Test
    void row08_whenAnInsuredSeniorParentsMedicalExpenditureIsClaimed_isRefused() {
        // Given — HealthInsuranceDeductionTest: s.126(7); the harness's parent of index 0
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs both = new EngineInputs(TY_2026_27, OLD, plain.temporal(), pay("1000000").withHealthPayments(List.of(
                new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM, false, List.of(0), Money.of("45000"), false, 1),
                new HealthPayment(HealthPaymentKind.MEDICAL_EXPENDITURE, false, List.of(0), Money.of("10000"), false, 1))),
                plain.assessee(), seniorParent(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());

        // When / Then
        assertThatThrownBy(() -> run(both)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RuleIds.DED_HEALTH_INSURANCE_PAID.value());
    }

    @Test
    void row09_whenTheInterestLeftOutOfHousePropertyIsNotWhatTheSection131LoanCarries_isRefused() {
        // Given — HousePropertyInterestTest: 1,50,000 said to be under s.131, the s.131 loan carrying 1,00,000
        HousingLoan split = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, Money.of("320000"),
                Money.ZERO, Money.of("150000"), true, false);
        HouseProperty let = new HouseProperty(HouseOccupancy.LET, Money.of("480000"), Money.of("540000"), Money.ZERO,
                false, Money.of("18000"), WHOLE, Money.ZERO, Optional.of(ACQUIRED), List.of(split));
        PurposeLoanInterest section131 = new PurposeLoanInterest(LoanPurpose.RESIDENTIAL_HOUSE, Money.of("100000"),
                LocalDate.of(2019, 6, 1), Money.of("3000000"), Optional.empty(), Optional.of(Money.of("4000000")), false);
        EngineInputs twice = inputs(OLD, withPurposeLoanInterest(pay("1000000").withHouseProperties(List.of(let)),
                List.of(section131)));

        // When / Then
        assertThatThrownBy(() -> run(twice)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enter the same amount on both sides");
    }

    @Test
    void row10_whenOwnNpsCouldCountUnderBothProvisions_countsEachRupeeOnce() {
        // Given — ChapterEightNodeTest: s.124(5) and (10), 80,000 of own NPS and nothing else
        EngineInputs nps = inputs(OLD, pay("1000000", component(ComponentCodes.EMPLOYEE_NPS, "80000")));

        // When
        PipelineRunner.Result run = run(nps);

        // Then — the two provisions' lines together hold no more than the 80,000 paid
        Money counted = run.trace().stream()
                .filter(line -> line.rule().equals(RuleIds.DED_NPS_OWN) || line.rule().equals(RuleIds.DED_SCHEDULE_XV))
                .map(StatutoryValidityTest::out).reduce(Money.ZERO, Money::plus);
        assertThat(counted).isEqualTo(Money.of("80000"));
    }

    @Test
    void row11_whenVoluntaryRetirementWasAllowedInAnotherYear_exemptsNothing() {
        // Given — RetirementNodeTest: s.19(2)(e)(ii), once only; allowed in TY 2021-22
        LedgerSnapshot plain = NodeHarness.plainTaxpayer().ledgers();
        LedgerSnapshot claimed = new LedgerSnapshot(plain.leaveTravelByBlock(),
                Map.of(RetirementEntry.VOLUNTARY_RETIREMENT, TaxYearId.of(2021)), plain.retirementAllowedBefore(),
                plain.housePropertyLossCarriedForward(), plain.educationLoanInterestFirstYear(),
                plain.capitalLossCarriedForward());
        EnteredFacts paid = pay("1000000", component(ComponentCodes.VRS_COMPENSATION, "300000"));
        EngineInputs again = inputs(OLD, withRetirementPayments(paid,
                List.of(RetirementPayment.voluntaryRetirement(0, ComponentCodes.VRS_COMPENSATION, false))), claimed);
        // Without the claim, the same 3,00,000 is pay with no exemption to claim: a special allowance
        EngineInputs taxedWhole = inputs(OLD, pay("1000000", component(ComponentCodes.SPECIAL_ALLOWANCE, "300000")), claimed);

        // When / Then
        keptOut(again, taxedWhole, RuleIds.RET_VOLUNTARY_RETIREMENT, ReasonCodes.RETIREMENT_ALLOWED_BEFORE);
    }

    @Test
    void row12_whenTheSalaryIsBelowTheStandardDeduction_deductsNoMoreThanTheSalary() {
        // Given — SalaryDeductionNodeTest: a part-year salary of 40,000 in the default regime
        EngineInputs small = inputs(NEW, pay("40000"));

        // When / Then — the standard deduction is the salary, and the total is nil, never below it
        assertThat(lines(small, RuleIds.SAL_STANDARD_DEDUCTION_NEW_TAX_REGIME)).singleElement()
                .satisfies(line -> assertThat(out(line)).isEqualTo(Money.of("40000")));
        assertThat(totalIncome(small)).isEqualTo(Money.ZERO);
    }

    @Test
    void row13_whenChapterEightExceedsGrossTotalIncome_deductsNoMoreThanIt() {
        // Given — ChapterEightNodeTest: s.122(2); basic 1,50,000 less 50,000 is 1,00,000 against 1,50,000 of tuition
        EngineInputs over = inputs(OLD, pay("150000").withQualifyingPayments(
                List.of(new QualifyingPayment(TUITION_FEES, Money.of("150000")))));

        // When
        PipelineRunner.Result run = run(over);

        // Then
        assertThat(run.valueOf(NodeIds.CHAPTERVIII)).isEqualTo(run.valueOf(NodeIds.GTI));
        assertThat(((MoneyValue) run.valueOf(NodeIds.TOTALINCOME)).amount()).isEqualTo(Money.ZERO);
    }

    private static com.thiru.wealthlens.taxengine.input.FamilySnapshot seniorParent() {
        return new com.thiru.wealthlens.taxengine.input.FamilySnapshot(List.of(
                new com.thiru.wealthlens.taxengine.input.FamilySnapshot.Member(
                        com.thiru.wealthlens.taxengine.input.FamilySnapshot.Relationship.PARENT, LocalDate.of(1955, 3, 1),
                        false, Optional.empty(), Optional.empty(), false, true)));
    }

    private static EnteredFacts withPurposeLoanInterest(EnteredFacts entered, List<PurposeLoanInterest> loans) {
        return replaced(entered, "purposeLoanInterest", loans);
    }

    private static EnteredFacts withRetirementPayments(EnteredFacts entered, List<RetirementPayment> payments) {
        return replaced(entered, "retirementPayments", payments);
    }

    /** The facts with one list replaced; EnteredFacts offers a {@code with…} for the lists the optimiser moves only. */
    private static EnteredFacts replaced(EnteredFacts entered, String component, Object value) {
        try {
            var components = EnteredFacts.class.getRecordComponents();
            Object[] args = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                args[i] = components[i].getName().equals(component) ? value : components[i].getAccessor().invoke(entered);
            }
            return EnteredFacts.class.getDeclaredConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("EnteredFacts has no component " + component, e);
        }
    }
}
