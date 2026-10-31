package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.AccommodationProvider;
import com.thiru.wealthlens.taxengine.input.EmployerAccommodation;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Furniture;
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
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
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
 * Residential accommodation as a perquisite (S4 r.15(2) Table I; Rules 1962 r.3(1) as substituted by G.S.R. 615(E);
 * D-111). Each figure is worked by hand from the Table's percentages — 10 / 7.5 / 5% by the 2011-census band, 10%
 * leased, 24% hotel, 10% a year of the furniture — on the accommodation "salary" less exempt allowances.
 */
class PerquisiteNodeTest {

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static DateRange wholeYear(int year) {
        return DateRange.between(LocalDate.of(year, 4, 1), LocalDate.of(year + 1, 3, 31));
    }

    private static EmployerAccommodation owned(int year, int cityPopulation, String rent) {
        return new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED, wholeYear(year), Optional.of(cityPopulation),
                Optional.empty(), Money.of(rent), Optional.empty(), TaxYearId.of(year), Optional.empty());
    }

    private static EmployerAccommodation paidFor(int year, AccommodationProvider provider, DateRange occupied,
            String cost, String rent) {
        return new EmployerAccommodation(0, provider, occupied, Optional.empty(), Optional.of(Money.of(cost)),
                Money.of(rent), Optional.empty(), TaxYearId.of(year), Optional.empty());
    }

    private static NodeResult run(int year, RegimeCode regime, String salary, String exempt,
            List<EmployerAccommodation> accommodations) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())), List.of(),
                        List.of(), List.of(), Optional.empty(), List.of(), accommodations, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.SALARY_BASE,
                        new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_ACCOM, List.of(Money.of(salary)))))
                .given(NodeIds.SALARY_EXEMPTIONS, MoneyValue.of(Money.of(exempt)))
                .given(NodeIds.SALARY_MONETARYINCOME, MoneyValue.of(Money.ZERO))
                .run(new PerquisiteNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOwnedInACityAboveFortyLakh_valuesItAtTenPercentLessRent(int year) {
        // When — 10% × 12,00,000 = 1,20,000, less 24,000 rent
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "1200000", "0",
                List.of(owned(year, 5_000_000, "24000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("96000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_ACCOM_OWNED);
            assertThat(line.kind()).isEqualTo(StepKind.INCOME);
            assertThat(line.provenance().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
        });
    }

    @Test
    void evaluate_whenOwnedInACityOfExactlyFortyLakh_valuesItAtSevenAndAHalfPercent() {
        // When — "exceeding forty lakhs" is strict: 40,00,000 is the middle band, 7.5% × 12,00,000 = 90,000 (D-258)
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(owned(2026, 4_000_000, "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("90000")));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.PERQ_ACCOM_OWNED));
    }

    @Test
    void evaluate_whenOwnedInTheOtherBands_valuesItAtSevenAndAHalfAndFivePercent() {
        // When — 7.5% and 5% of 12,00,000
        NodeResult middle = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0",
                List.of(owned(2026, 2_000_000, "0")));
        NodeResult other = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0",
                List.of(owned(2026, 1_000_000, "0")));

        // Then
        assertThat(middle.value()).isEqualTo(MoneyValue.of(Money.of("90000")));
        assertThat(other.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenLeased_takesTheLowerOfTheRentalAndTenPercent(int year) {
        // When — least of 1,80,000 rental and 10% × 12,00,000 = 1,20,000
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(
                paidFor(year, AccommodationProvider.EMPLOYER_LEASED, wholeYear(year), "180000", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("120000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAHotelForAQuarter_takesTwentyFourPercentOfThePeriodsSalary(int year) {
        // When — 1 Apr – 30 Jun, 91 of 365 days: 12,00,000 × 91/365 = 2,99,178.08; × 24% = 71,802.74, below 90,000
        DateRange quarter = DateRange.between(LocalDate.of(year, 4, 1), LocalDate.of(year, 6, 30));
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "1200000", "0",
                List.of(paidFor(year, AccommodationProvider.HOTEL, quarter, "90000", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("71802.74")));
    }

    @Test
    void evaluate_whenFurnished_addsTenPercentAYearOfTheFurnituresCost() {
        // Given — owned, above forty lakh, furniture costing 2,00,000
        EmployerAccommodation furnished = new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED,
                wholeYear(2026), Optional.of(5_000_000), Optional.empty(), Money.of("24000"),
                Optional.of(Furniture.owned(Money.of("200000"), Money.ZERO)), TaxYearId.of(2026), Optional.empty());

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(furnished));

        // Then — 96,000 + 10% × 2,00,000 = 20,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("116000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.PERQ_ACCOM_OWNED, RuleIds.PERQ_ACCOM_FURNITURE);
    }

    @Test
    void evaluate_whenAllowancesWereExempt_leavesThemOutOfTheSalary() {
        // When — r.15(8)(k)(iii): 12,00,000 less 50,000 exempt = 11,50,000; 10% = 1,15,000
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, "1200000", "50000",
                List.of(owned(2026, 5_000_000, "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("115000")));
    }

    @Test
    void evaluate_whenProvidedByTheGovernment_takesTheLicenceFeeLessRent() {
        // When — licence fee 30,000 less 6,000 rent
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(
                paidFor(2026, AccommodationProvider.GOVERNMENT, wholeYear(2026), "30000", "6000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("24000")));
    }

    @Test
    void evaluate_whenInTheNewRegime_isStillSalary() {
        // When — a perquisite is salary in every regime
        NodeResult result = run(2026, NEW_TAX_REGIME, "1200000", "0",
                List.of(owned(2026, 5_000_000, "24000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("96000")));
    }

    private static EmployerAccommodation continued(int year, AccommodationProvider provider, int firstProvidedIn,
            Optional<String> cost, Optional<String> firstYearValue) {
        return new EmployerAccommodation(0, provider, wholeYear(year),
                provider == AccommodationProvider.EMPLOYER_OWNED ? Optional.of(5_000_000)
                        : Optional.empty(),
                cost.map(Money::of), Money.ZERO, Optional.empty(), TaxYearId.of(firstProvidedIn),
                firstYearValue.map(Money::of));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenOwnedAccommodationContinuesFromTheFirstTaxYear_isCappedByTheIndex(int year) {
        // Given — first provided 2023-24 at 1,00,000; this year 10% of 12,00,000 = 1,20,000 (D-115)
        EmployerAccommodation home = continued(year, AccommodationProvider.EMPLOYER_OWNED, 2023, Optional.empty(),
                Optional.of("100000"));

        // When
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home));

        // Then — 1,00,000 × 376 / 348 = 1,08,045.98; 1,00,000 × 384 / 348 = 1,10,344.83
        assertThat(result.value())
                .isEqualTo(MoneyValue.of(Money.of(year == 2025 ? "108045.98" : "110344.83")));
        assertThat(result.trace().getFirst().limitedBy())
                .hasValueSatisfying(bound -> assertThat(bound).contains("Cost Inflation Index"));
    }

    @Test
    void evaluate_whenTheContinuedValueIsBelowTheCap_isTheValue() {
        // Given — 10% of 10,50,000 = 1,05,000 against a cap of 1,10,344.83
        EmployerAccommodation home = continued(2026, AccommodationProvider.EMPLOYER_OWNED, 2023, Optional.empty(),
                Optional.of("100000"));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, "1050000", "0", List.of(home));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("105000")));
    }

    @Test
    void evaluate_whenRentIsPaidOnContinuedAccommodation_capsTheValueAfterRent() {
        // Given — the Sl. 2(a) amount is "as reduced by the rent" (D-115 C3): 1,20,000 − 24,000 = 96,000
        EmployerAccommodation home = new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED,
                wholeYear(2026), Optional.of(5_000_000), Optional.empty(), Money.of("24000"),
                Optional.empty(), TaxYearId.of(2023), Optional.of(Money.of("100000")));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home));

        // Then — 96,000 is under the cap of 1,10,344.83; capping before rent would give 1,10,344.83 − 24,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("96000")));
    }

    @Test
    void evaluate_whenLeasedAccommodationContinuesInTaxYear2025_isCappedToo() {
        // Given — G.S.R. 636(E): "owned or taken on lease or rent"; first provided 2024-25 at 90,000; this year the
        // lower of rental 1,00,000 and 10% of 12,00,000
        EmployerAccommodation home = continued(2025, AccommodationProvider.EMPLOYER_LEASED, 2024,
                Optional.of("100000"), Optional.of("90000"));

        // When
        NodeResult result = run(2025, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home));

        // Then — 90,000 × 376 / 363 = 93,223.14
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("93223.14")));
    }

    /**
     * Every provider, first provided 2023-24 (index 348), on a salary of 12,00,000. The cap's row reaches owned and
     * leased accommodation only (W-154a): government and hotel accommodation enter no first-year amount, so were the
     * row to reach them they would be refused for it.
     */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("continuedByProvider")
    void evaluate_whenContinuedFromTheFirstTaxYear_isCappedOnlyWhereTheCapsRowReachesTheProvider(int year,
            AccommodationProvider provider, Optional<String> cost, Optional<String> firstYearValue, String expected,
            boolean capped) {
        // Given
        EmployerAccommodation home = continued(year, provider, 2023, cost, firstYearValue);

        // When
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(expected)));
        assertThat(result.trace().getFirst().limitedBy()).hasValueSatisfying(bound ->
                assertThat(bound.contains("Cost Inflation Index")).isEqualTo(capped));
    }

    private static Stream<Arguments> continuedByProvider() {
        Optional<String> none = Optional.empty();
        return Stream.of(
                // 10% of 12,00,000 = 1,20,000; cap 1,00,000 × 376 / 348 = 1,08,045.98, × 384 / 348 = 1,10,344.83
                Arguments.of(2025, AccommodationProvider.EMPLOYER_OWNED, none, Optional.of("100000"), "108045.98",
                        true),
                Arguments.of(2026, AccommodationProvider.EMPLOYER_OWNED, none, Optional.of("100000"), "110344.83",
                        true),
                // the lower of 1,00,000 and 1,20,000; cap 90,000 × 376 / 348 = 97,241.38, × 384 / 348 = 99,310.34
                Arguments.of(2025, AccommodationProvider.EMPLOYER_LEASED, Optional.of("100000"), Optional.of("90000"),
                        "97241.38", true),
                Arguments.of(2026, AccommodationProvider.EMPLOYER_LEASED, Optional.of("100000"), Optional.of("90000"),
                        "99310.34", true),
                // the licence fee 30,000, uncapped
                Arguments.of(2025, AccommodationProvider.GOVERNMENT, Optional.of("30000"), none, "30000", false),
                Arguments.of(2026, AccommodationProvider.GOVERNMENT, Optional.of("30000"), none, "30000", false),
                // the lower of 90,000 and 24% of 12,00,000 = 2,88,000, uncapped
                Arguments.of(2025, AccommodationProvider.HOTEL, Optional.of("90000"), none, "90000", false),
                Arguments.of(2026, AccommodationProvider.HOTEL, Optional.of("90000"), none, "90000", false));
    }

    @Test
    void evaluate_whenContinuedWithoutTheFirstYearsAmount_isRefused() {
        // Given
        EmployerAccommodation home = continued(2026, AccommodationProvider.EMPLOYER_OWNED, 2024, Optional.empty(),
                Optional.empty());

        // When / Then
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first tax year");
    }

    @Test
    void evaluate_whenFirstProvidedBeforeTheFirstTaxYear_isRefusedNamingTheQuestion() {
        // Given — first provided 2022-23 (D-115 C2a)
        EmployerAccommodation home = continued(2026, AccommodationProvider.EMPLOYER_OWNED, 2022, Optional.empty(),
                Optional.of("100000"));

        // When / Then
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-11");
    }

    private static EmployerAccommodation entered(int year, AccommodationProvider provider, Optional<Integer> population,
            Optional<String> cost, Optional<Furniture> furniture, int firstProvidedIn, Optional<String> firstYearValue) {
        return new EmployerAccommodation(0, provider, wholeYear(year), population, cost.map(Money::of), Money.ZERO,
                furniture, TaxYearId.of(firstProvidedIn), firstYearValue.map(Money::of));
    }

    /** What the provider's row reads must be entered: the owned row counts the population, the leased reads the cost. */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("missingFacts")
    void evaluate_whenAFactTheProvidersRowReadsIsMissing_isRefusedAskingForIt(int year, AccommodationProvider provider,
            Optional<Integer> population, Optional<String> cost, String expected) {
        // Given
        EmployerAccommodation home = entered(year, provider, population, cost, Optional.empty(), year,
                Optional.empty());

        // When / Then
        assertThatThrownBy(() -> run(year, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expected);
    }

    private static Stream<Arguments> missingFacts() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (int year : new int[] {2025, 2026}) {
            cases.add(Arguments.of(year, AccommodationProvider.EMPLOYER_OWNED, Optional.empty(), Optional.empty(),
                    "Accommodation 1 (EMPLOYER_OWNED) is measured on the city's population (PERQ-ACCOM-OWNED);"
                            + " enter it"));
            cases.add(Arguments.of(year, AccommodationProvider.EMPLOYER_LEASED, Optional.empty(), Optional.empty(),
                    "Accommodation 1 (EMPLOYER_LEASED) is measured on the employer's cost (PERQ-ACCOM-LEASED);"
                            + " enter it"));
        }
        return cases.build();
    }

    /** A fact the provider's row does not read would be ignored: only the owned row counts a population. */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("unreadFacts")
    void evaluate_whenAFactTheProvidersRowDoesNotReadIsGiven_isRefused(int year, AccommodationProvider provider,
            Optional<Integer> population, Optional<String> cost, String expected) {
        // Given
        EmployerAccommodation home = entered(year, provider, population, cost, Optional.empty(), year,
                Optional.empty());

        // When / Then
        assertThatThrownBy(() -> run(year, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expected);
    }

    private static Stream<Arguments> unreadFacts() {
        Optional<Integer> city = Optional.of(5_000_000);
        Optional<String> cost = Optional.of("100000");
        Stream.Builder<Arguments> cases = Stream.builder();
        for (int year : new int[] {2025, 2026}) {
            cases.add(Arguments.of(year, AccommodationProvider.EMPLOYER_LEASED, city, cost,
                    "(EMPLOYER_LEASED) is not measured on the city's population (PERQ-ACCOM-LEASED)"));
            cases.add(Arguments.of(year, AccommodationProvider.GOVERNMENT, city, cost,
                    "(GOVERNMENT) is not measured on the city's population (PERQ-ACCOM-GOVERNMENT)"));
            cases.add(Arguments.of(year, AccommodationProvider.HOTEL, city, cost,
                    "(HOTEL) is not measured on the city's population (PERQ-ACCOM-HOTEL)"));
            cases.add(Arguments.of(year, AccommodationProvider.EMPLOYER_OWNED, city, cost,
                    "(EMPLOYER_OWNED) is not measured on the employer's cost (PERQ-ACCOM-OWNED)"));
        }
        return cases.build();
    }

    /**
     * A first year's amount is read only by the continued cap, which reaches owned and leased accommodation provided
     * before this year: on government or hotel accommodation, or on owned accommodation first provided this year, it
     * would be ignored.
     */
    @ParameterizedTest(name = "TY {0} {1} first provided {4}")
    @MethodSource("uncappedFirstYearValues")
    void evaluate_whenAFirstYearsAmountIsGivenWhereNoCapReadsIt_isRefused(int year, AccommodationProvider provider,
            Optional<Integer> population, Optional<String> cost, int firstProvidedIn) {
        // Given
        EmployerAccommodation home = entered(year, provider, population, cost, Optional.empty(), firstProvidedIn,
                Optional.of("90000"));

        // When / Then
        assertThatThrownBy(() -> run(year, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-PERQ-ACCOM-CONTINUED caps it")
                .hasMessageContaining("the first year's amount would be ignored, so it is refused");
    }

    private static Stream<Arguments> uncappedFirstYearValues() {
        Optional<String> cost = Optional.of("100000");
        Stream.Builder<Arguments> cases = Stream.builder();
        for (int year : new int[] {2025, 2026}) {
            cases.add(Arguments.of(year, AccommodationProvider.GOVERNMENT, Optional.empty(), cost, 2023));
            cases.add(Arguments.of(year, AccommodationProvider.HOTEL, Optional.empty(), cost, 2023));
            cases.add(Arguments.of(year, AccommodationProvider.EMPLOYER_OWNED, Optional.of(5_000_000),
                    Optional.empty(), year));
        }
        return cases.build();
    }

    /**
     * Table I adds furniture to Sl. 1 and 2 only; the hotel's Sl. 3 has no furnished value of its own to add it to
     * (G.S.R. 615(E) col. 4; Rules 2026 r.15(2)(e)(i), "Table I: Sl. Nos. 1 and 2"; W-158b).
     */
    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAHotelIsFurnished_isRefusedAsNoRowValuesItsFurniture(int year) {
        // Given
        EmployerAccommodation hotel = entered(year, AccommodationProvider.HOTEL, Optional.empty(),
                Optional.of("90000"), Optional.of(Furniture.owned(Money.of("200000"), Money.ZERO)), year,
                Optional.empty());

        // When / Then
        assertThatThrownBy(() -> run(year, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(hotel)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accommodation 1 (HOTEL): no row of RS-PERQ-ACCOM-FURNITURE values its"
                        + " furniture");
    }

    /**
     * Furnished Government and leased accommodation take the furniture too (Sl. 1, 2(b)). Government: the licence fee
     * 30,000 + 10% of 2,00,000 = 50,000. Leased: the lower of 1,00,000 and 10% of 12,00,000 = 1,00,000, + 20,000 =
     * 1,20,000.
     */
    @ParameterizedTest(name = "TY {0} {1}")
    @MethodSource("furnishedProviders")
    void evaluate_whenGovernmentOrLeasedAccommodationIsFurnished_addsTheFurniture(int year,
            AccommodationProvider provider, String cost, String expected) {
        // Given
        EmployerAccommodation home = entered(year, provider, Optional.empty(), Optional.of(cost),
                Optional.of(Furniture.owned(Money.of("200000"), Money.ZERO)), year, Optional.empty());

        // When
        NodeResult result = run(year, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(home));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of(expected)));
        assertThat(result.trace()).extracting(TraceLine::rule).last().isEqualTo(RuleIds.PERQ_ACCOM_FURNITURE);
    }

    private static Stream<Arguments> furnishedProviders() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (int year : new int[] {2025, 2026}) {
            cases.add(Arguments.of(year, AccommodationProvider.GOVERNMENT, "30000", "50000"));
            cases.add(Arguments.of(year, AccommodationProvider.EMPLOYER_LEASED, "100000", "120000"));
        }
        return cases.build();
    }

    @Test
    void evaluate_whenNoAccommodationIsProvided_isNilAndSaysNothing() {
        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0", List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }
}
