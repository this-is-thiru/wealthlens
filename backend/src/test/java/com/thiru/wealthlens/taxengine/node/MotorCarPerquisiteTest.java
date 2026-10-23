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
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSet;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    private static RulePack shipped(TaxYearId taxYear) {
        return new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
    }

    private static NodeResult run(int year, String monetaryIncome, Optional<CompanyEmployer> company,
            List<EmployerVehicle> vehicles) {
        return runOn(shipped(TaxYearId.of(year)), year, monetaryIncome, company, vehicles);
    }

    private static NodeResult runOn(RulePack pack, int year, String monetaryIncome, Optional<CompanyEmployer> company,
            List<EmployerVehicle> vehicles) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(Collections.nCopies(employmentsNamed(vehicles),
                        new Employment(List.of(), true, EmployerKind.OTHER, false, company)),
                        List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), vehicles, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
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

    /** As many employments as the vehicles name, so a case can spread cars over employments. */
    private static int employmentsNamed(List<EmployerVehicle> vehicles) {
        return vehicles.stream().mapToInt(EmployerVehicle::employment).max().orElse(0) + 1;
    }

    private static NodeResult run(int year, List<EmployerVehicle> vehicles) {
        return run(year, ABOVE_THRESHOLD, Optional.empty(), vehicles);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenPartlyOfficialSmallCarWithChauffeur_isTheMonthlyAmountsForTwelveMonths(int year) {
        // When
        NodeResult result = run(year, List.of(partly(CarEngine.ofCapacity(1500), true, 12)));

        // Then — TY 2026-27 (5,000 + 3,000) × 12 = 96,000; TY 2025-26 (1,800 + 900) × 12 = 32,400
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "96000" : "32400")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_CAR_PARTLY_SMALL);
            assertThat(line.provenance().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheEmployeeMeetsPrivateRunningOfALargeCar_isTheLowerMonthlyAmount(int year) {
        // Given
        EmployerVehicle car = employerCar(VehicleUse.PARTLY_OFFICIAL, CarEngine.ofCapacity(2000), true, "0", Optional.empty(), "0");

        // When
        NodeResult result = run(year, List.of(car));

        // Then — 3,000 × 12 = 36,000; 900 × 12 = 10,800
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "36000" : "10800")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheEngineIsExactly1600Cc_takesTheLowerColumn(int year) {
        // When — "does not exceed 1.6 litres": 1,600 cc does not exceed it (DR 01 PERQ-CAR-2026)
        NodeResult result = run(year, List.of(partly(CarEngine.ofCapacity(1600), false, 12)));

        // Then
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.PERQ_CAR_PARTLY_SMALL));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheEngineIs1601Cc_takesTheHigherColumn(int year) {
        // When — one cc over 1.6 litres exceeds it
        NodeResult result = run(year, List.of(partly(CarEngine.ofCapacity(1601), false, 12)));

        // Then
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.PERQ_CAR_PARTLY_LARGE));
    }

    @Test
    void evaluate_whenNoColumnCoversACarThatIsNotElectric_isRefusedNamingItsCapacity() {
        // Given — TY 2026-27's Sl. 1(c)(i) cut down to its electric row: no row covers a 1,500 cc car
        RulePack shipped = shipped(TaxYearId.of(2026));
        Map<RuleSetId, RuleSet> sets = new HashMap<>(shipped.ruleSets());
        RuleSet partly = sets.get(RuleSetIds.RS_PERQ_CAR_PARTLY);
        sets.put(partly.id(), new RuleSet(partly.id(), partly.selection(), partly.ground(),
                List.of(RuleIds.PERQ_CAR_PARTLY_ELECTRIC)));
        RulePack gap = new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), shipped.rules(), sets,
                shipped.components(), shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());

        // When / Then — a pack defect, said as one, not as the electric car's refusal
        assertThatThrownBy(() -> runOn(gap, 2026, ABOVE_THRESHOLD, Optional.empty(),
                        List.of(partly(CarEngine.ofCapacity(1500), false, 12))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("covers a car of 1500 cc")
                .hasMessageNotContaining("electric");
    }

    @Test
    void evaluate_whenProvidedForSixMonths_countsSixMonths() {
        // When — 5,000 × 6
        NodeResult result = run(2026, List.of(partly(CarEngine.ofCapacity(1500), false, 6)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenWhollyPrivate_isRunningPlusWearAndTearLessTheCharge(int year) {
        // Given
        EmployerVehicle car = employerCar(VehicleUse.WHOLLY_PRIVATE, CarEngine.ofCapacity(2000), false, "120000", Optional.of(Money.of("800000")), "12000");

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
                Optional.of(CarEngine.ofCapacity(2000)), "150000", OfficialUseRecord.none())));

        // Then — 1,50,000 − 7,000 × 12 = 66,000; 1,50,000 − 2,400 × 12 = 1,21,200
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "66000" : "121200")));
    }

    @Test
    void evaluate_whenAHigherOfficialUseIsClaimedOnRecords_takesItOffInstead() {
        // Given
        OfficialUseRecord records = new OfficialUseRecord(true, Optional.of(Money.of("120000")));

        // When
        NodeResult result = run(2026, List.of(ownVehicle(VehicleOwner.EMPLOYEE_CAR,
                Optional.of(CarEngine.ofCapacity(2000)), "150000", records)));

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
        EmployerVehicle second = employerCar(VehicleUse.PARTLY_OFFICIAL, CarEngine.ofCapacity(2000), false, "100000", Optional.of(Money.of("1000000")), "0");

        // When
        NodeResult result = run(2026, List.of(partly(CarEngine.ofCapacity(1500), false, 12), second));

        // Then — 5,000 × 12 = 60,000, plus 1,00,000 + 10% × 10,00,000 = 2,00,000: 2,60,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("260000")));
        assertThat(result.trace()).extracting(line -> line.rule())
                .containsExactly(RuleIds.PERQ_CAR_PARTLY_SMALL, RuleIds.PERQ_CAR_PRIVATE);
    }

    private static EmployerVehicle employerCarIn(int employment, VehicleUse use, int cc, String running,
            Optional<Money> cost, OfficialUseRecord record) {
        return new EmployerVehicle(employment, VehicleOwner.EMPLOYER_CAR, use, Optional.of(CarEngine.ofCapacity(cc)),
                false, false, 12, Money.of(running), cost, Money.ZERO, record);
    }

    /**
     * Three employer cars, one employment (r.15(3)(b); r.3(2) proviso; PERQ-CAR-MULTI-001): the first entered of the two
     * not wholly official per month whatever its own use — 5,000 / 1,800 × 12 — the other at running and wear, 1,00,000
     * + 10% × 10,00,000 = 2,00,000; the wholly official one nil and not counted (W-154e3).
     */
    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenSeveralCarsOneWhollyOfficial_valuesTheFirstSharedPerMonthAndTheOtherAtCost(int year) {
        // Given
        EmployerVehicle first = employerCarIn(0, VehicleUse.WHOLLY_PRIVATE, 1500, "0", Optional.empty(),
                OfficialUseRecord.none());
        EmployerVehicle second = employerCarIn(0, VehicleUse.PARTLY_OFFICIAL, 2000, "100000",
                Optional.of(Money.of("1000000")), OfficialUseRecord.none());
        EmployerVehicle official = employerCarIn(0, VehicleUse.WHOLLY_OFFICIAL, 1500, "0", Optional.empty(),
                new OfficialUseRecord(true, Optional.empty()));

        // When
        NodeResult result = run(year, List.of(first, second, official));

        // Then
        assertThat(result.trace()).extracting(line -> line.rule()).containsExactly(RuleIds.PERQ_CAR_PARTLY_SMALL,
                RuleIds.PERQ_CAR_PRIVATE, RuleIds.PERQ_CAR_PATH_OFFICIAL);
        assertThat(result.trace()).extracting(line -> line.out()).containsExactly(
                MoneyValue.of(Money.of(year == 2026 ? "60000" : "21600")), MoneyValue.of(Money.of("200000")),
                MoneyValue.of(Money.ZERO));
    }

    /** One car in each of two employments: each alone, so each per month (W-154e3). */
    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenEachEmploymentHasOneCar_eachIsValuedAlone(int year) {
        // Given
        List<EmployerVehicle> cars = List.of(
                employerCarIn(0, VehicleUse.PARTLY_OFFICIAL, 1500, "0", Optional.empty(), OfficialUseRecord.none()),
                employerCarIn(1, VehicleUse.PARTLY_OFFICIAL, 1500, "0", Optional.empty(), OfficialUseRecord.none()));

        // When
        NodeResult result = run(year, cars);

        // Then — 5,000 / 1,800 × 12 each
        String each = year == 2026 ? "60000" : "21600";
        assertThat(result.trace()).extracting(line -> line.rule())
                .containsExactly(RuleIds.PERQ_CAR_PARTLY_SMALL, RuleIds.PERQ_CAR_PARTLY_SMALL);
        assertThat(result.trace()).extracting(line -> line.out())
                .containsExactly(MoneyValue.of(Money.of(each)), MoneyValue.of(Money.of(each)));
    }

    /** The employee's own car is not one of the employer's cars, so the employer's car stays alone (W-154e3). */
    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAnEmployersCarAndTheEmployeesOwn_theEmployersIsAlone(int year) {
        // Given
        List<EmployerVehicle> vehicles = List.of(partly(CarEngine.ofCapacity(1500), false, 12),
                ownVehicle(VehicleOwner.EMPLOYEE_CAR, Optional.of(CarEngine.ofCapacity(2000)), "150000",
                        OfficialUseRecord.none()));

        // When
        NodeResult result = run(year, vehicles);

        // Then — 5,000 / 1,800 × 12; 1,50,000 less 7,000 / 2,400 × 12
        assertThat(result.trace()).extracting(line -> line.rule())
                .containsExactly(RuleIds.PERQ_CAR_PARTLY_SMALL, RuleIds.PERQ_CAR_EMPLOYEE);
        assertThat(result.trace()).extracting(line -> line.out()).containsExactly(
                MoneyValue.of(Money.of(year == 2026 ? "60000" : "21600")),
                MoneyValue.of(Money.of(year == 2026 ? "66000" : "121200")));
    }

    @Test
    void evaluate_whenMonetarySalaryIsExactlyTheThreshold_theEmployersCarIsNotAPerquisite() {
        // When — 4,00,000 does not "exceed" 4,00,000
        NodeResult result = run(2026, "400000", Optional.empty(), List.of(partly(CarEngine.ofCapacity(1500), false, 12)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.PERQ_SPECIFIED_EMPLOYEE));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenMonetarySalaryExceedsTheThresholdByOneRupee_theEmployersCarIsValued(int year) {
        // When
        NodeResult result = run(year, "400001", Optional.empty(), List.of(partly(CarEngine.ofCapacity(1500), false, 12)));

        // Then — 5,000 × 12; 1,800 × 12
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(year == 2026 ? "60000" : "21600")));
    }

    /**
     * An employee who is not specified (monetary salary 4,00,000, not over 4,00,000), each owner used partly officially
     * for 12 months: the employer's car is nil on PERQ-SPECIFIED-EMPLOYEE (s.17(1)(c)); the employee's own car and other
     * conveyance meet the employee's obligation and are valued for anyone (s.17(1)(f)) — 1,00,000 less 7,000 × 12 =
     * 16,000, or less 2,400 × 12 = 71,200; 50,000 less 3,000 × 12 = 14,000, or less 900 × 12 = 39,200 (W-154e1).
     */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("ownersForOneWhoIsNotSpecified")
    void evaluate_whenTheEmployeeIsNotSpecified_onlyTheEmployersOwnCarIsNil(int year, VehicleOwner owner,
            RuleId expectedRow, String expected) {
        // Given
        EmployerVehicle vehicle = switch (owner) {
            case EMPLOYER_CAR -> partly(CarEngine.ofCapacity(1500), false, 12);
            case EMPLOYEE_CAR -> ownVehicle(owner, Optional.of(CarEngine.ofCapacity(2000)), "100000",
                    OfficialUseRecord.none());
            case EMPLOYEE_OTHER_CONVEYANCE -> ownVehicle(owner, Optional.empty(), "50000", OfficialUseRecord.none());
        };

        // When
        NodeResult result = run(year, "400000", Optional.empty(), List.of(vehicle));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(expected)));
        assertThat(result.trace()).singleElement().satisfies(line -> assertThat(line.rule()).isEqualTo(expectedRow));
    }

    private static Stream<Arguments> ownersForOneWhoIsNotSpecified() {
        return Stream.of(
                Arguments.of(2025, VehicleOwner.EMPLOYER_CAR, RuleIds.PERQ_SPECIFIED_EMPLOYEE, "0"),
                Arguments.of(2026, VehicleOwner.EMPLOYER_CAR, RuleIds.PERQ_SPECIFIED_EMPLOYEE, "0"),
                Arguments.of(2025, VehicleOwner.EMPLOYEE_CAR, RuleIds.PERQ_CAR_EMPLOYEE, "71200"),
                Arguments.of(2026, VehicleOwner.EMPLOYEE_CAR, RuleIds.PERQ_CAR_EMPLOYEE, "16000"),
                Arguments.of(2025, VehicleOwner.EMPLOYEE_OTHER_CONVEYANCE, RuleIds.PERQ_CAR_CONVEYANCE, "39200"),
                Arguments.of(2026, VehicleOwner.EMPLOYEE_OTHER_CONVEYANCE, RuleIds.PERQ_CAR_CONVEYANCE, "14000"));
    }

    @Test
    void evaluate_whenADirectorOfTheCompanyEarnsLittle_theEmployersCarIsStillValued() {
        // When
        NodeResult result = run(2026, "100000", Optional.of(new CompanyEmployer(true, false)),
                List.of(partly(CarEngine.ofCapacity(1500), false, 12)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @Test
    void evaluate_whenAnElectricCarInTaxYear2026_isValuedInTheLowerColumn() {
        // When — "or the motor car is an electric vehicle": 5,000 × 12
        NodeResult result = run(2026, List.of(partly(CarEngine.electricVehicle(), false, 12)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @Test
    void evaluate_whenAnElectricCarInTaxYear2025_isRefused() {
        // When / Then — the 1962 Table has no electric column (D-113 C5a)
        assertThatThrownBy(() -> run(2025, List.of(partly(CarEngine.electricVehicle(), false, 12))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("electric");
    }

    private static EmployerVehicle whollyOfficial(VehicleOwner owner, OfficialUseRecord record) {
        Optional<CarEngine> engine = owner == VehicleOwner.EMPLOYEE_OTHER_CONVEYANCE ? Optional.empty()
                : Optional.of(CarEngine.ofCapacity(1500));
        return new EmployerVehicle(0, owner, VehicleUse.WHOLLY_OFFICIAL, engine, false, false, 12, Money.ZERO,
                Optional.empty(), Money.ZERO, record);
    }

    /** Wholly official with the records: "Nil, if the prescribed documents are maintained" (PERQ-CAR-2026; D-330 C5a). */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("yearsAndOwners")
    void evaluate_whenWhollyOfficialWithRecords_isANilLineOnTheOfficialPath(int year, VehicleOwner owner) {
        // When
        NodeResult result = run(year, List.of(whollyOfficial(owner, new OfficialUseRecord(true, Optional.empty()))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.PERQ_CAR_PATH_OFFICIAL));
    }

    /** Without the records the Table states no value, so no path row reaches the vehicle (D-113 C7; W-154e2). */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("yearsAndOwners")
    void evaluate_whenWhollyOfficialWithoutRecords_isRefusedAsNoPathReachesIt(int year, VehicleOwner owner) {
        // When / Then
        assertThatThrownBy(() -> run(year, List.of(whollyOfficial(owner, OfficialUseRecord.none()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-PERQ-CAR-PATH")
                .hasMessageContaining("vehicle.journeys.and.certificate.kept=false");
    }

    private static Stream<Arguments> yearsAndOwners() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (int year : new int[] {2025, 2026}) {
            for (VehicleOwner owner : VehicleOwner.values()) {
                cases.add(Arguments.of(year, owner));
            }
        }
        return cases.build();
    }

    /**
     * Each path a specified employee's vehicle takes, both years, 12 months: the employer's 1,500 cc car partly official
     * — employer runs, 5,000 / 1,800 × 12; employee runs, 2,000 / 600 × 12 — wholly private, 1,20,000 + 10% × 8,00,000
     * − 12,000; the employee's 2,000 cc car, 1,50,000 less 7,000 / 2,400 × 12; a conveyance, 50,000 less 3,000 / 900 ×
     * 12 (Table II; W-154e2).
     */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("paths")
    void evaluate_whenEachPath_isValuedOnTheRowItsComputationReads(int year, String path, EmployerVehicle vehicle,
            RuleId expectedRow, String expected) {
        // When
        NodeResult result = run(year, List.of(vehicle));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(expected)));
        assertThat(result.trace()).singleElement().satisfies(line -> assertThat(line.rule()).isEqualTo(expectedRow));
    }

    private static Stream<Arguments> paths() {
        EmployerVehicle employerRuns = partly(CarEngine.ofCapacity(1500), false, 12);
        EmployerVehicle employeeRuns = employerCar(VehicleUse.PARTLY_OFFICIAL, CarEngine.ofCapacity(1500), true, "0",
                Optional.empty(), "0");
        EmployerVehicle whollyPrivate = employerCar(VehicleUse.WHOLLY_PRIVATE, CarEngine.ofCapacity(2000), false,
                "120000", Optional.of(Money.of("800000")), "12000");
        EmployerVehicle ownCar = ownVehicle(VehicleOwner.EMPLOYEE_CAR, Optional.of(CarEngine.ofCapacity(2000)),
                "150000", OfficialUseRecord.none());
        EmployerVehicle conveyance = ownVehicle(VehicleOwner.EMPLOYEE_OTHER_CONVEYANCE, Optional.empty(), "50000",
                OfficialUseRecord.none());
        return Stream.of(
                Arguments.of(2025, "employer runs", employerRuns, RuleIds.PERQ_CAR_PARTLY_SMALL, "21600"),
                Arguments.of(2026, "employer runs", employerRuns, RuleIds.PERQ_CAR_PARTLY_SMALL, "60000"),
                Arguments.of(2025, "employee runs", employeeRuns, RuleIds.PERQ_CAR_OWNRUN_SMALL, "7200"),
                Arguments.of(2026, "employee runs", employeeRuns, RuleIds.PERQ_CAR_OWNRUN_SMALL, "24000"),
                Arguments.of(2025, "wholly private", whollyPrivate, RuleIds.PERQ_CAR_PRIVATE, "188000"),
                Arguments.of(2026, "wholly private", whollyPrivate, RuleIds.PERQ_CAR_PRIVATE, "188000"),
                Arguments.of(2025, "own car", ownCar, RuleIds.PERQ_CAR_EMPLOYEE, "121200"),
                Arguments.of(2026, "own car", ownCar, RuleIds.PERQ_CAR_EMPLOYEE, "66000"),
                Arguments.of(2025, "conveyance", conveyance, RuleIds.PERQ_CAR_CONVEYANCE, "39200"),
                Arguments.of(2026, "conveyance", conveyance, RuleIds.PERQ_CAR_CONVEYANCE, "14000"));
    }

    @Test
    void evaluate_whenAChargeIsEnteredForAFixedAmountRow_isRefused() {
        // Given
        EmployerVehicle car = employerCar(VehicleUse.PARTLY_OFFICIAL, CarEngine.ofCapacity(1500), false, "0", Optional.empty(), "1000");

        // When / Then
        assertThatThrownBy(() -> run(2026, List.of(car)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("charged");
    }
}
