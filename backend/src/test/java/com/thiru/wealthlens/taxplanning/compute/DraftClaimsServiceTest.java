package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A draft's claims on the engine alone (W-118): which are made, their measures, and their steps' dates. Figures as
 * {@code ClaimPricingTest} works them; dates are the harness's: TY 2026-27 ends on 31 March 2027, and its return is due
 * on 31 July 2027.
 */
class DraftClaimsServiceTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final DraftClaimsService SERVICE = new DraftClaimsService(null, null, CALCULATOR, null);

    private static final LocalDate YEAR_END = LocalDate.of(2027, 3, 31);

    private static final LocalDate RETURN_DUE = LocalDate.of(2027, 7, 31);

    private static EngineInputs tuitionPaid(String regime) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment pay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1000000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        return new EngineInputs(TaxYearId.of(2026), new RegimeCode(regime), plain.temporal(),
                EnteredFacts.none().withEmployments(List.of(pay)).withQualifyingPayments(
                        List.of(new QualifyingPayment(new ComponentCode("TUITION_FEES"), Money.of("50000")))),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    @Test
    void claimed_whenTuitionIsPaidInTheOldRegime_measuresItAndDatesItsSteps() {
        // When
        List<DraftClaims.Claim> claimed = SERVICE.claimed(tuitionPaid("FIRST_SCHEDULE_RATES"));

        // Then — 9,50,000 → 9,00,000: 1,06,600 against 96,200, so 10,400; step 2 is by the tax year's end, step 4 by the
        // return's due date, steps 1 and 3 by none and the employer's cut-off
        assertThat(claimed).singleElement().satisfies(claim -> {
            assertThat(claim.playbook().rule()).isEqualTo("DED-SCHEDULE-XV");
            assertThat(claim.allowed()).contains(Money.of("50000"));
            assertThat(claim.worth()).contains(Money.of("10400"));
            assertThat(claim.stepDates()).containsExactly(new DraftClaims.StepDate(2, YEAR_END),
                    new DraftClaims.StepDate(4, RETURN_DUE));
        });
    }

    @Test
    void notClaimed_whenOneClaimIsMade_listsTheYearsOthersUnmeasured() {
        // Given
        EngineInputs inputs = tuitionPaid("FIRST_SCHEDULE_RATES");

        // When
        List<DraftClaims.Claim> others = SERVICE.notClaimed(inputs, SERVICE.claimed(inputs));

        // Then — the year's other seven, by rule id as the pack holds them; HRA's last step alone is dated
        assertThat(others).extracting(claim -> claim.playbook().rule()).containsExactly("DED-DONATION-UNREPORTED",
                "DED-HEALTH-FAMILY", "DED-NPS-OWN", "HP-OWN-RESIDENCE-INTEREST-CAP", "SAL-HOUSE-RENT-ALLOWANCE",
                "SAL-LEAVE-TRAVEL-CONCESSION", "SAL-TAX-ON-EMPLOYMENT");
        assertThat(others).allSatisfy(claim -> {
            assertThat(claim.allowed()).isEmpty();
            assertThat(claim.worth()).isEmpty();
        });
        assertThat(others.get(4).stepDates()).containsExactly(new DraftClaims.StepDate(4, RETURN_DUE));
    }

    @Test
    void claimed_whenTheDefaultRegimeAllowsNothing_isEmpty() {
        // When / Then — s.202(2)(a)
        assertThat(SERVICE.claimed(tuitionPaid("NEW_TAX_REGIME"))).isEmpty();
    }
}
