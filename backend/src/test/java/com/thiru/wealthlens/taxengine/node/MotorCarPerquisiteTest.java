package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CarEngine;
import com.thiru.wealthlens.taxengine.input.CompanyEmployer;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.EmployerVehicle;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.OfficialUseRecord;
import com.thiru.wealthlens.taxengine.input.VehicleOwner;
import com.thiru.wealthlens.taxengine.input.VehicleUse;
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
 * A vehicle the employer provides or runs, per calendar month (S4 r.15(3) Table II; Rules 1962 r.3(2); D-113). Each
 * figure is worked by hand from the Tables — TY 2026-27 ₹5,000 / ₹7,000, ₹2,000 / ₹3,000, chauffeur ₹3,000,
 * conveyance ₹3,000; TY 2025-26 Rs. 1,800 / 2,400, Rs. 600 / 900, chauffeur Rs. 900, conveyance Rs. 900; wear and
 * tear 10% a year of cost — and from the ₹4,00,000 specified-employee amount (S4 r.17; r.3C).
 */
class MotorCarPerquisiteTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final String ABOVE_THRESHOLD = "900000";

    private static EmployerVehicle employerCar(VehicleUse use, CarEngine engine, boolean employeeMeetsPrivateRunning,
            String running, Optional<Money> cost, String charged) {
        return new EmployerVehicle(0, VehicleOwner.EMPLOYER_CAR, use, Optional.of(engine), false,
                employeeMeetsPrivateRunning, 12, Money.of(running), cost, Money.of(charged), OfficialUseRecord.none());
    }

    private static EmployerVehicle partly(CarEngine engine, boolean chauffeur, int months) {
        return new EmployerVehicle(0, VehicleOwner.EMPLOYER_CAR, VehicleUse.PARTLY_OFFICIAL, Optional.of(engine),
                chauffeur, false, months, Money.ZERO, Optional.empty(), Money.ZERO, OfficialUseRecord.none());
    }

    private static EmployerVehicle ownVehicle(VehicleOwner owner, Optional<CarEngine> engine, String spent,
            OfficialUseRecord record) {
        return new EmployerVehicle(0, owner, VehicleUse.PARTLY_OFFICIAL, engine, false, false, 12, Money.of(spent),
                Optional.empty(), Money.ZERO, record);
    }

    private static NodeResult run(int year, String monetaryIncome, Optional<CompanyEmployer> company,
            List<EmployerVehicle> vehicles) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, company)),
                        List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), vehicles, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of()),
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

    private static NodeResult run(int year, List<EmployerVehicle> vehicles) {
        return run(year, ABOVE_THRESHOLD, Optional.empty(), vehicles);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenPartlyOfficialSmallCarWithChauffeur_isTheMonthlyAmountsForTwelveMonths(int year) {
        // When
        NodeResult result = run(year, List.of(partly(CarEngine.UP_TO_1600_CC, true, 12)));

        // Then — TY 2026-27 (5,000 + 3,000) × 12 = 96,000; TY 2025-26 (1,800 + 900) × 12 = 32,400
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "96000" : "32400")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_CAR_PARTLY_SMALL);
            assertThat(line.provenance().verificationLevel()).isEqualTo(year == 2025
                    ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheEmployeeMeetsPrivateRunningOfALargeCar_isTheLowerMonthlyAmount(int year) {
        // Given
        EmployerVehicle car = employerCar(VehicleUse.PARTLY_OFFICIAL, CarEngine.ABOVE_1600_CC, true, "0", Optional.empty(), "0");

        // When
        NodeResult result = run(year, List.of(car));

        // Then — 3,000 × 12 = 36,000; 900 × 12 = 10,800
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "36000" : "10800")));
    }

    @Test
    void evaluate_whenProvidedForSixMonths_countsSixMonths() {
        // When — 5,000 × 6
        NodeResult result = run(2026, List.of(partly(CarEngine.UP_TO_1600_CC, false, 6)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenWhollyPrivate_isRunningPlusWearAndTearLessTheCharge(int year) {
        // Given
        EmployerVehicle car = employerCar(VehicleUse.WHOLLY_PRIVATE, CarEngine.ABOVE_1600_CC, false, "120000", Optional.of(Money.of("800000")), "12000");

        // When
        NodeResult result = run(year, List.of(car));

        // Then — 1,20,000 + 10% × 8,00,000 − 12,000 = 1,88,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("188000")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_CAR_PRIVATE);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheEmployeesOwnLargeCarIsRunPartlyPrivately_isTheExpenditureLessTheTableAmount(int year) {
        // When — below the threshold: the employee's own car is salary for everyone
        NodeResult result = run(year, "300000", Optional.empty(), List.of(ownVehicle(VehicleOwner.EMPLOYEE_CAR,
                Optional.of(CarEngine.ABOVE_1600_CC), "150000", OfficialUseRecord.none())));

        // Then — 1,50,000 − 7,000 × 12 = 66,000; 1,50,000 − 2,400 × 12 = 1,21,200
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "66000" : "121200")));
    }

    @Test
    void evaluate_whenAHigherOfficialUseIsClaimedOnRecords_takesItOffInstead() {
        // Given
        OfficialUseRecord records = new OfficialUseRecord(true, Optional.of(Money.of("120000")));

        // When
        NodeResult result = run(2026, List.of(ownVehicle(VehicleOwner.EMPLOYEE_CAR,
                Optional.of(CarEngine.ABOVE_1600_CC), "150000", records)));

        // Then — least of 1,50,000 − 84,000 = 66,000 and 1,50,000 − 1,20,000 = 30,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAnOtherConveyanceIsRunPartlyPrivately_isTheExpenditureLessTheMonthlyAmount(int year) {
        // When
        NodeResult result = run(year, List.of(ownVehicle(VehicleOwner.EMPLOYEE_OTHER_CONVEYANCE, Optional.empty(),
                "50000", OfficialUseRecord.none())));

        // Then — 50,000 − 3,000 × 12 = 14,000; 50,000 − 900 × 12 = 39,200
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "14000" : "39200")));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_CAR_CONVEYANCE);
    }

    @Test
    void evaluate_whenTwoEmployerCarsAreUsedPrivately_valuesTheFirstAtTheFixedAmountAndTheOtherAtCost() {
        // Given
        EmployerVehicle second = employerCar(VehicleUse.PARTLY_OFFICIAL, CarEngine.ABOVE_1600_CC, false, "100000", Optional.of(Money.of("1000000")), "0");

        // When
        NodeResult result = run(2026, List.of(partly(CarEngine.UP_TO_1600_CC, false, 12), second));

        // Then — 5,000 × 12 = 60,000, plus 1,00,000 + 10% × 10,00,000 = 2,00,000: 2,60,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("260000")));
        assertThat(result.trace()).extracting(line -> line.rule())
                .containsExactly(RuleIds.PERQ_CAR_PARTLY_SMALL, RuleIds.PERQ_CAR_PRIVATE);
    }

    @Test
    void evaluate_whenMonetarySalaryIsExactlyTheThreshold_theEmployersCarIsNotAPerquisite() {
        // When — 4,00,000 does not "exceed" 4,00,000
        NodeResult result = run(2026, "400000", Optional.empty(), List.of(partly(CarEngine.UP_TO_1600_CC, false, 12)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.PERQ_SPECIFIED_EMPLOYEE));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenMonetarySalaryExceedsTheThresholdByOneRupee_theEmployersCarIsValued(int year) {
        // When
        NodeResult result = run(year, "400001", Optional.empty(), List.of(partly(CarEngine.UP_TO_1600_CC, false, 12)));

        // Then — 5,000 × 12; 1,800 × 12
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "60000" : "21600")));
    }

    @Test
    void evaluate_whenADirectorOfTheCompanyEarnsLittle_theEmployersCarIsStillValued() {
        // When
        NodeResult result = run(2026, "100000", Optional.of(new CompanyEmployer(true, false)),
                List.of(partly(CarEngine.UP_TO_1600_CC, false, 12)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @Test
    void evaluate_whenAnElectricCarInTaxYear2026_isValuedInTheLowerColumn() {
        // When — "or the motor car is an electric vehicle": 5,000 × 12
        NodeResult result = run(2026, List.of(partly(CarEngine.ELECTRIC, false, 12)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @Test
    void evaluate_whenAnElectricCarInTaxYear2025_isRefused() {
        // When / Then — the 1962 Table has no electric column (D-113 C5a)
        assertThatThrownBy(() -> run(2025, List.of(partly(CarEngine.ELECTRIC, false, 12))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("electric");
    }

    @Test
    void evaluate_whenWhollyOfficialWithRecords_hasNoValueAndNoLine() {
        // Given
        EmployerVehicle car = new EmployerVehicle(0, VehicleOwner.EMPLOYER_CAR, VehicleUse.WHOLLY_OFFICIAL,
                Optional.of(CarEngine.UP_TO_1600_CC), false, false, 12, Money.ZERO, Optional.empty(), Money.ZERO,
                new OfficialUseRecord(true, Optional.empty()));

        // When
        NodeResult result = run(2026, List.of(car));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void evaluate_whenWhollyOfficialWithoutRecords_isRefused() {
        // Given
        EmployerVehicle car = new EmployerVehicle(0, VehicleOwner.EMPLOYER_CAR, VehicleUse.WHOLLY_OFFICIAL,
                Optional.of(CarEngine.UP_TO_1600_CC), false, false, 12, Money.ZERO, Optional.empty(), Money.ZERO,
                OfficialUseRecord.none());

        // When / Then
        assertThatThrownBy(() -> run(2026, List.of(car)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("certificate");
    }

    @Test
    void evaluate_whenAChargeIsEnteredForAFixedAmountRow_isRefused() {
        // Given
        EmployerVehicle car = employerCar(VehicleUse.PARTLY_OFFICIAL, CarEngine.UP_TO_1600_CC, false, "0", Optional.empty(), "1000");

        // When / Then
        assertThatThrownBy(() -> run(2026, List.of(car)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("charged");
    }
}
