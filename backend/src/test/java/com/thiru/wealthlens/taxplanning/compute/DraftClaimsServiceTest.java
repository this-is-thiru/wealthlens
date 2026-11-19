package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
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
import java.util.Map;
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
            assertThat(claim.finalAmount()).hasValueSatisfying(amount -> {
                assertThat(amount.changes()).isTrue();
                assertThat(amount.says()).isEmpty();
            });
        });
    }

    @Test
    void claimed_whenAClaimLeavesTheFinalAmountWhereItWas_listsItWithItsWorthAndSaysSo() {
        // Given — 40 of tuition and 99,996 deducted at source, as ClaimFinalAmountTest works it: worth 8.32; the balance
        // 6,604 without the claim and 6,595.68 with it both round to 6,600
        EngineInputs tuition = tuitionPaid("FIRST_SCHEDULE_RATES");
        EngineInputs inputs = new EngineInputs(tuition.taxYear(), tuition.regime(), tuition.temporal(),
                tuition.entered().withQualifyingPayments(
                        List.of(new QualifyingPayment(new ComponentCode("TUITION_FEES"), Money.of("40")))),
                tuition.assessee(), tuition.family(), tuition.ledgers(), tuition.elections(), tuition.capitalGains(),
                new FilingFacts(RETURN_DUE, Optional.empty(), List.of(), Money.of("99996"), List.of(), Map.of(),
                        Optional.empty()));

        // When
        List<DraftClaims.Claim> claimed = SERVICE.claimed(inputs);

        // Then — listed, not dropped (F40.5)
        assertThat(claimed).singleElement().satisfies(claim -> {
            assertThat(claim.worth()).contains(Money.of("8.32"));
            assertThat(claim.finalAmount()).hasValueSatisfying(amount -> {
                assertThat(amount.withClaim()).isEqualTo(Money.of("6600"));
                assertThat(amount.withoutClaim()).isEqualTo(Money.of("6600"));
                assertThat(amount.changes()).isFalse();
                assertThat(amount.says()).hasValueSatisfying(says -> assertThat(says)
                        .startsWith("Making this claim does not change what you finally pay"));
            });
        });
    }

    @Test
    void notClaimed_whenOneClaimIsMade_listsTheYearsOthersUnmeasured() {
        // Given
        EngineInputs inputs = tuitionPaid("FIRST_SCHEDULE_RATES");

        // When
        List<DraftClaims.Claim> others = SERVICE.notClaimed(inputs, SERVICE.claimed(inputs));

        // Then — the year's other thirteen, by rule id as the pack holds them (W-117c added five, W-117e one); HRA's last
        // step is dated
        assertThat(others).extracting(claim -> claim.playbook().rule()).containsExactly("DED-DEPOSIT-INTEREST-SENIOR",
                "DED-DISABILITY", "DED-DISABLED-DEPENDANT", "DED-DONATION-UNREPORTED", "DED-DONATION-WHOLE", "DED-HEALTH-FAMILY",
                "DED-NPS-EMPLOYER-OTHER", "DED-NPS-OWN", "DED-SAVINGS-INTEREST", "HP-OWN-RESIDENCE-INTEREST-CAP",
                "SAL-HOUSE-RENT-ALLOWANCE", "SAL-LEAVE-TRAVEL-CONCESSION", "SAL-TAX-ON-EMPLOYMENT");
        assertThat(others).allSatisfy(claim -> {
            assertThat(claim.allowed()).isEmpty();
            assertThat(claim.worth()).isEmpty();
            assertThat(claim.finalAmount()).isEmpty();
        });
        assertThat(others.get(10).stepDates()).containsExactly(new DraftClaims.StepDate(4, RETURN_DUE));
    }

    @Test
    void claimed_whenTheDefaultRegimeAllowsNothing_isEmpty() {
        // When / Then — s.202(2)(a)
        assertThat(SERVICE.claimed(tuitionPaid("NEW_TAX_REGIME"))).isEmpty();
    }
}
