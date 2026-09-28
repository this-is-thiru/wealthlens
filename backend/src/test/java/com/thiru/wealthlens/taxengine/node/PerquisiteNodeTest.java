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
import com.thiru.wealthlens.taxengine.input.PopulationBand;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

    private static EmployerAccommodation owned(int year, PopulationBand band, String rent) {
        return new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED, wholeYear(year), Optional.of(band),
                Optional.empty(), Money.of(rent), Optional.empty(), TaxYearId.of(year));
    }

    private static EmployerAccommodation paidFor(int year, AccommodationProvider provider, DateRange occupied,
            String cost, String rent) {
        return new EmployerAccommodation(0, provider, occupied, Optional.empty(), Optional.of(Money.of(cost)),
                Money.of(rent), Optional.empty(), TaxYearId.of(year));
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
                        List.of(), List.of(), Optional.empty(), List.of(), accommodations, List.of(), List.of()),
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
                List.of(owned(year, PopulationBand.ABOVE_FORTY_LAKH, "24000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("96000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.PERQ_ACCOM_OWNED);
            assertThat(line.kind()).isEqualTo(StepKind.INCOME);
            assertThat(line.provenance().verificationLevel()).isEqualTo(year == 2025
                    ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
        });
    }

    @Test
    void evaluate_whenOwnedInTheOtherBands_valuesItAtSevenAndAHalfAndFivePercent() {
        // When — 7.5% and 5% of 12,00,000
        NodeResult middle = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0",
                List.of(owned(2026, PopulationBand.FIFTEEN_TO_FORTY_LAKH, "0")));
        NodeResult other = run(2026, FIRST_SCHEDULE_RATES, "1200000", "0",
                List.of(owned(2026, PopulationBand.ANY_OTHER_PLACE, "0")));

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
                wholeYear(2026), Optional.of(PopulationBand.ABOVE_FORTY_LAKH), Optional.empty(), Money.of("24000"),
                Optional.of(Furniture.owned(Money.of("200000"), Money.ZERO)), TaxYearId.of(2026));

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
                List.of(owned(2026, PopulationBand.ABOVE_FORTY_LAKH, "0")));

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
                List.of(owned(2026, PopulationBand.ABOVE_FORTY_LAKH, "24000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("96000")));
    }

    @Test
    void evaluate_whenOwnedAccommodationContinuesFromAnEarlierYear_isRefusedUntilTheIndexIsRead() {
        // Given — first provided in 2024-25: r.15(2)(d)'s Cost Inflation Index cap applies (D-111 C5a)
        EmployerAccommodation continued = new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED,
                wholeYear(2026), Optional.of(PopulationBand.ABOVE_FORTY_LAKH), Optional.empty(), Money.ZERO,
                Optional.empty(), TaxYearId.of(2024));

        // When / Then
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, "1200000", "0", List.of(continued)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cost Inflation Index");
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
