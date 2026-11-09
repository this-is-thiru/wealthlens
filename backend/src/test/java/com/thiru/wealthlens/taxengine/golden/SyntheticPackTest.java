package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.RegimeComparison;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.election.RegimeOffer;
import com.thiru.wealthlens.taxengine.election.RegimeOffers;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidator;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The acceptance test for the brief's Open/Closed claim (W-146; {@code DR 10} E8, {@code DR 17} T17-11's data half,
 * {@code DR 18} T18-13): three synthetic years, three regimes and a limit that changes on 1 July, none of them in the
 * shipped law, loaded through the production boot path and computed by the production {@link TaxCalculator} — with no
 * production source written for them.
 *
 * <p>The pack is {@code rulepacks-synthetic/}, written by {@code docs/taxplanning-build/tools/w146_synthetic_pack.py}:
 * the synthetic year's own law, and every other row the pipeline reads copied from TY 2026-27 with its regimes renamed
 * (W-245 (a), D-464). Every expected figure is worked by hand in {@code plans/W-146.md}, Revision 1 and its build
 * record, before any run.
 */
class SyntheticPackTest {

    private static final String ROOT = "rulepacks-synthetic";

    private static final ShippedRulePacks PACKS = ShippedRulePacks.compile(ROOT,
            ClasspathRulePackProvider.shippedYears(ROOT));

    private static final TaxCalculator CALCULATOR = new TaxCalculator(PACKS);

    private static final TaxYearId TY_2099 = TaxYearId.of(2099);

    private static final TaxYearId TY_2100 = TaxYearId.of(2100);

    private static final TaxYearId TY_2101 = TaxYearId.of(2101);

    private static final RegimeCode SYN_DEFAULT = new RegimeCode("SYN_DEFAULT");

    private static final RegimeCode SYN_CLASSIC = new RegimeCode("SYN_CLASSIC");

    private static final RegimeCode SYN_FLAT = new RegimeCode("SYN_FLAT");

    private static final String SALARY = "2000000";

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2099, 2100, 2101})
    void validate_whenEachSyntheticYearIsLoaded_passesEveryPackInvariant(int year) {
        // Given — loaded through the boot path, its lock verified
        TaxYearId taxYear = TaxYearId.of(year);

        // When / Then
        assertThatCode(() -> PackValidator.validate(PACKS.load(taxYear))).doesNotThrowAnyException();
    }

    @Test
    void offers_whenAResidentIn2099_offersAllThreeRegimes() {
        // Given
        EngineInputs inputs = inputs(TY_2099, SYN_DEFAULT, ResidentialStatus.RESIDENT);

        // When
        List<RegimeOffer> offers = RegimeOffers.of(PACKS.load(TY_2099), inputs);

        // Then
        assertThat(offers).allMatch(offer -> offer instanceof RegimeOffer.Available)
                .extracting(RegimeOffer::code).containsExactlyInAnyOrder(SYN_DEFAULT, SYN_CLASSIC, SYN_FLAT);
    }

    @Test
    void offers_whenANonResidentIn2099_barsTheFlatRegimeNamingItsCondition() {
        // Given — SYN_FLAT is open to residents only (ELIG-RESIDENT)
        EngineInputs inputs = inputs(TY_2099, SYN_DEFAULT, ResidentialStatus.NON_RESIDENT);

        // When
        List<RegimeOffer> offers = RegimeOffers.of(PACKS.load(TY_2099), inputs);

        // Then
        assertThat(offers).filteredOn(offer -> offer instanceof RegimeOffer.Available)
                .extracting(RegimeOffer::code).containsExactlyInAnyOrder(SYN_DEFAULT, SYN_CLASSIC);
        assertThat(offers).filteredOn(offer -> offer.code().equals(SYN_FLAT)).singleElement()
                .isInstanceOfSatisfying(RegimeOffer.Barred.class, barred -> assertThat(barred.reason())
                        .contains("a resident").contains("does not hold"));
    }

    @Test
    void compute_whenTheDefaultRegimeIn2099_proratesTheStandardDeductionChangedOn1July() {
        // Given — 50,000 for 91 days and 80,000 for 274: 12,465.75 + 60,054.79 = 72,520.55
        EngineInputs inputs = inputs(TY_2099, SYN_DEFAULT, ResidentialStatus.RESIDENT);

        // When
        ComputedTax tax = CALCULATOR.compute(inputs);

        // Then — 20,00,000 − 72,520.55 = 19,27,479.45 → 19,27,480; 10 % × 14,27,480 = 1,42,748; cess 5,709.92
        assertThat(tax.totalIncome()).isEqualTo(Money.of("1927480"));
        assertThat(tax.bill()).isEqualTo(Money.of("148457.92"));
        assertThat(tax.finalAmount()).isEqualTo(Money.of("148460"));
        assertThat(standardDeduction(tax).rule()).isEqualTo("SYN-STANDARD-DEDUCTION");
        assertThat(standardDeduction(tax).limitedBy())
                .contains("2099-04-01 to 2099-06-30").contains("2099-07-01 to 2100-03-31");
    }

    @Test
    void compute_whenTheClassicRegimeIn2099_appliesItsOwnLadder() {
        // Given
        EngineInputs inputs = inputs(TY_2099, SYN_CLASSIC, ResidentialStatus.RESIDENT);

        // When
        ComputedTax tax = CALCULATOR.compute(inputs);

        // Then — 19,27,480; 20 % × 16,27,480 = 3,25,496; cess 13,019.84
        assertThat(tax.totalIncome()).isEqualTo(Money.of("1927480"));
        assertThat(tax.bill()).isEqualTo(Money.of("338515.84"));
        assertThat(tax.finalAmount()).isEqualTo(Money.of("338520"));
    }

    @Test
    void compute_whenTheFlatRegimeIn2099_chargesOneRateAndTakesNoneOfTheExceptedDeduction() {
        // Given
        EngineInputs inputs = inputs(TY_2099, SYN_FLAT, ResidentialStatus.RESIDENT);

        // When
        ComputedTax tax = CALCULATOR.compute(inputs);

        // Then — 20,00,000 at 8 % = 1,60,000; cess 6,400; the 1 July row ExceptIn(SYN_FLAT) is not in the trace, its own
        // nil row is
        assertThat(tax.totalIncome()).isEqualTo(Money.of("2000000"));
        assertThat(tax.bill()).isEqualTo(Money.of("166400"));
        assertThat(tax.finalAmount()).isEqualTo(Money.of("166400"));
        assertThat(tax.trace()).extracting(ComputedTax.Step::rule).doesNotContain("SYN-STANDARD-DEDUCTION")
                .contains("SYN-STANDARD-DEDUCTION-FLAT");
    }

    @Test
    void compareRegimes_whenAResidentIn2099_pricesAllThreeAndChoosesTheCheapest() {
        // Given
        EngineInputs inputs = inputs(TY_2099, SYN_DEFAULT, ResidentialStatus.RESIDENT);

        // When
        RegimeComparison comparison = CALCULATOR.compareRegimes(inputs);

        // Then — 1,48,457.92 < 1,66,400 < 3,38,515.84: against SYN_DEFAULT, +17,942.08 and +1,90,057.92
        assertThat(comparison.chosen()).isEqualTo("SYN_DEFAULT");
        assertThat(comparison.priced()).extracting(RegimeComparison.Priced::regime)
                .containsExactlyInAnyOrder("SYN_DEFAULT", "SYN_CLASSIC", "SYN_FLAT");
        Money base = billOf(comparison, "SYN_DEFAULT");
        assertThat(base).isEqualTo(Money.of("148457.92"));
        assertThat(billOf(comparison, "SYN_FLAT").minus(base)).isEqualTo(Money.of("17942.08"));
        assertThat(billOf(comparison, "SYN_CLASSIC").minus(base)).isEqualTo(Money.of("190057.92"));
    }

    @Test
    void compute_whenTheDefaultRegimeIn2100_appliesTheOneVersionAllYear() {
        // Given
        EngineInputs inputs = inputs(TY_2100, SYN_DEFAULT, ResidentialStatus.RESIDENT);

        // When
        ComputedTax tax = CALCULATOR.compute(inputs);

        // Then — 20,00,000 − 80,000 = 19,20,000; 10 % × 14,20,000 = 1,42,000; × 1.04
        assertThat(tax.totalIncome()).isEqualTo(Money.of("1920000"));
        assertThat(tax.bill()).isEqualTo(Money.of("147680"));
        assertThat(tax.finalAmount()).isEqualTo(Money.of("147680"));
    }

    @Test
    void compareRegimes_whenTheFlatRegimeHasLapsedBy2101_pricesTheTwoInForce() {
        // Given — SYN_FLAT is in force to 2101-03-31, so the TY 2101-02 pack does not hold it
        EngineInputs inputs = inputs(TY_2101, SYN_DEFAULT, ResidentialStatus.RESIDENT);

        // When
        RegimeComparison comparison = CALCULATOR.compareRegimes(inputs);

        // Then
        assertThat(PACKS.load(TY_2101).regimes()).doesNotContainKey(SYN_FLAT);
        assertThat(comparison.priced()).extracting(RegimeComparison.Priced::regime)
                .containsExactlyInAnyOrder("SYN_DEFAULT", "SYN_CLASSIC");
    }

    private static ComputedTax.Step standardDeduction(ComputedTax tax) {
        return tax.trace().stream().filter(step -> step.rule().startsWith("SYN-STANDARD-DEDUCTION")).findFirst()
                .orElseThrow();
    }

    private static Money billOf(RegimeComparison comparison, String regime) {
        return comparison.priced().stream().filter(priced -> priced.regime().equals(regime)).findFirst()
                .orElseThrow().bill();
    }

    /**
     * Basic pay of 20,00,000, a resident or not, computed on the year's first day with the return due four months after
     * it ends, so no interest or fee arises.
     */
    private static EngineInputs inputs(TaxYearId year, RegimeCode regime, ResidentialStatus residence) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment employment = new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of(SALARY))),
                false, EmployerKind.OTHER, false, Optional.empty());
        return new EngineInputs(year, regime, TemporalContext.forTaxYear(year, year.startsOn()), entered(employment),
                new Individual(LocalDate.of(2060, 6, 15), residence, Optional.empty(), Optional.empty()),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                FilingFacts.notYetFurnished(year.endsOn().plusMonths(4)));
    }

    private static EnteredFacts entered(Employment employment) {
        return new EnteredFacts(List.of(employment), List.of(), List.of(), List.of(), Optional.empty(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Optional.empty(), List.of());
    }
}
