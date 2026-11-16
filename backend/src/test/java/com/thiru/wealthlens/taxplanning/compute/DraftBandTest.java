package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxplanning.workspace.OnboardingStep;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The coarse figure's band on the engine alone (W-122b, D-720): the five steps walked in F34's order on one persona, each
 * band worked by hand from the rulebook. TY 2026-27, old regime, born 1985 (under 60), no family, no HRA.
 */
class DraftBandTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final DraftComputationService SERVICE =
            new DraftComputationService(null, CALCULATOR, null, null, null, null, null, null);

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final ComponentCode TUITION_FEES = new ComponentCode("TUITION_FEES");

    private static EngineInputs inputs(String regime, EnteredFacts entered) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, new RegimeCode(regime), plain.temporal(), entered, plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    /** Step 1's answer: the year's gross pay, ₹12,00,000, as one fully taxable component (W-122 C2a). */
    private static EnteredFacts pay() {
        return EnteredFacts.none().withEmployments(List.of(new Employment(
                List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of("1200000"))), false, EmployerKind.OTHER, false,
                Optional.empty())));
    }

    private static Optional<Band> band(EnteredFacts entered, Set<OnboardingStep> answered) {
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", entered);
        ComputedTax tax = CALCULATOR.compute(inputs);
        return SERVICE.band(inputs, tax, answered);
    }

    private static Band coarse(EnteredFacts entered, Set<OnboardingStep> answered) {
        return band(entered, answered).orElseThrow(() -> new AssertionError("expected a coarse band for " + answered));
    }

    @Test
    void band_whenTheStepsAreAnsweredInOrder_narrowsToTheBillAndThenIsNotCoarse() {
        // Given — standard deduction 50,000, so 11,50,000 taxable as the draft stands
        EnteredFacts invested = pay().withQualifyingPayments(List.of(new QualifyingPayment(TUITION_FEES, Money.of("150000"))));

        // When
        Band one = coarse(pay(), EnumSet.of(OnboardingStep.PAY));
        Band two = coarse(pay(), EnumSet.of(OnboardingStep.PAY, OnboardingStep.PAY_STRUCTURE_AND_RENT));
        Band three = coarse(pay(), EnumSet.of(OnboardingStep.PAY, OnboardingStep.PAY_STRUCTURE_AND_RENT,
                OnboardingStep.EMPLOYER_FUNDS));
        Band four = coarse(invested, EnumSet.complementOf(EnumSet.of(OnboardingStep.OTHER_INCOME_AND_PROPERTY)));
        Optional<Band> five = band(invested, EnumSet.allOf(OnboardingStep.class));

        // Then — upper: 1,12,500 + 30% × 1,50,000 = 1,57,500, cess 6,300: 1,63,800. Step 1's lower end fills Schedule XV
        // 1,50,000, health 25,000, own NPS 50,000 and rent paid 5,000 × 12 = 60,000: 8,65,000 taxable, 12,500 + 20% ×
        // 3,65,000 = 85,500, cess 3,420: 88,920
        assertThat(one.upper()).isEqualTo(Money.of("163800"));
        assertThat(one.lower()).isEqualTo(Money.of("88920"));
        // step 2 answered, no rent: 9,25,000 taxable, 12,500 + 20% × 4,25,000 = 97,500, cess 3,900: 1,01,400
        assertThat(two.upper()).isEqualTo(Money.of("163800"));
        assertThat(two.lower()).isEqualTo(Money.of("101400"));
        // step 3 answered, nothing entered: no change
        assertThat(three).usingRecursiveComparison().isEqualTo(two);
        // step 4, Schedule XV 1,50,000 entered: 10,00,000 taxable, 1,12,500, cess 4,500: 1,17,000 both ends
        assertThat(four.upper()).isEqualTo(Money.of("117000"));
        assertThat(four.lower()).isEqualTo(Money.of("117000"));
        assertThat(five).isEmpty();
        // each band inside the one before
        for (Band[] pair : new Band[][] {{one, two}, {two, three}, {three, four}}) {
            assertThat(pair[1].lower()).isGreaterThanOrEqualTo(pair[0].lower());
            assertThat(pair[1].upper()).isLessThanOrEqualTo(pair[0].upper());
        }
        assertThat(one.says()).startsWith("This is a coarse figure");
    }

    @Test
    void band_whenNoStepIsAnswered_isCoarse() {
        // When / Then — the same figures as step 1, since step 1's facts are what is entered
        Band none = coarse(pay(), Set.of());
        assertThat(none.upper()).isEqualTo(Money.of("163800"));
        assertThat(none.lower()).isEqualTo(Money.of("88920"));
    }

    @Test
    void band_whenTheRegimeAllowsNoRoom_isCoarseWithBothEndsTheBill() {
        // Given — the default regime allows none of the four rooms
        EngineInputs inputs = inputs("NEW_TAX_REGIME", pay());
        ComputedTax tax = CALCULATOR.compute(inputs);

        // When
        Band band = SERVICE.band(inputs, tax, EnumSet.of(OnboardingStep.PAY)).orElseThrow();

        // Then
        assertThat(band.lower()).isEqualTo(band.upper());
        assertThat(band.upper()).isEqualTo(tax.bill());
    }
}
