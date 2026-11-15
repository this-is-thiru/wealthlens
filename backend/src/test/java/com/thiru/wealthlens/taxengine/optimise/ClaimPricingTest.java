package com.thiru.wealthlens.taxengine.optimise;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.ClaimPlaybook;
import com.thiru.wealthlens.taxengine.api.PricedClaim;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.DonationClause;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Each claim in a computation and what it is worth (W-118 C1a), worked by hand from the TY 2026-27 rows, as
 * {@code DeductionRoomPricingTest} states them: {@code SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE} ₹50,000;
 * {@code RATE-SLAB-FIRST-SCHEDULE} nil to 2,50,000, 5 % to 5,00,000, 20 % to 10,00,000; cess 4 %. Basic pay 10,00,000
 * less 50,000 is 9,50,000 before Chapter VIII.
 */
class ClaimPricingTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final TaxCalculator CALCULATOR =
            new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)));

    private static final ComponentCode TUITION_FEES = new ComponentCode("TUITION_FEES");

    private static final List<QualifyingPayment> TUITION = List.of(new QualifyingPayment(TUITION_FEES, Money.of("50000")));

    private static final List<HealthPayment> OWN_PREMIUM = List.of(new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM,
            true, List.of(), Money.of("25000"), false, 1));

    private static EngineInputs inputs(String regime, EnteredFacts entered) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, new RegimeCode(regime), plain.temporal(), entered, plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    private static EnteredFacts paid(List<QualifyingPayment> payments, List<HealthPayment> health) {
        Employment basicPay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1000000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        return EnteredFacts.none().withEmployments(List.of(basicPay)).withQualifyingPayments(payments)
                .withHealthPayments(health);
    }

    @Test
    void priceClaims_whenTuitionAndAPremiumAreClaimedInTheOldRegime_measuresEachAgainstTheOtherAsItStands() {
        // Given
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES", paid(TUITION, OWN_PREMIUM));

        // When
        List<PricedClaim> claims = CALCULATOR.priceClaims(inputs);

        // Then — with both, 9,50,000 − 50,000 − 25,000 = 8,75,000: 12,500 + 75,000 = 87,500 + 3,500 = 91,000. Without
        // the investments, 9,25,000: 12,500 + 85,000 = 97,500 + 3,900 = 1,01,400, so 10,400. Without the premium,
        // 9,00,000: 12,500 + 80,000 = 92,500 + 3,700 = 96,200, so 5,200. By rule id, as the pack holds them
        assertThat(claims).extracting(claim -> claim.playbook().rule())
                .containsExactly("DED-HEALTH-FAMILY", "DED-SCHEDULE-XV");
        assertThat(claims.get(0).allowed()).isEqualTo(Money.of("25000"));
        assertThat(claims.get(0).worth()).isEqualTo(Money.of("5200"));
        assertThat(claims.get(1).allowed()).isEqualTo(Money.of("50000"));
        assertThat(claims.get(1).worth()).isEqualTo(Money.of("10400"));
    }

    @Test
    void priceClaims_whenTheRegimeDisallowsTheClaims_findsNone() {
        // Given — the default regime allows neither (s.202(2)(a))
        EngineInputs inputs = inputs("NEW_TAX_REGIME", paid(TUITION, OWN_PREMIUM));

        // When / Then
        assertThat(CALCULATOR.priceClaims(inputs)).isEmpty();
    }

    @Test
    void priceClaims_whenADonationToANamedFundIsMade_pricesItAsTheDonationsClaim() {
        // Given — ₹10,000 to a named fund, deducted whole under DED-DONATION-WHOLE (s.133(1)(a)), which the donations
        // entry leads (W-117e C1a); the reported-donation entry's rule is the s.133(6) bar and stays unpriced
        EnteredFacts entered = paid(List.of(), List.of());
        EnteredFacts withDonation = new EnteredFacts(entered.employments(), List.of(), List.of(), List.of(),
                Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                List.of(new Donation(DonationClause.NAMED_FUND, Money.of("10000"), false, true)), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of());

        // When
        List<PricedClaim> claims = CALCULATOR.priceClaims(inputs("FIRST_SCHEDULE_RATES", withDonation));

        // Then — with it, 9,50,000 − 10,000 = 9,40,000: 12,500 + 88,000 = 1,00,500 + 4,020 = 1,04,520; without it,
        // 1,06,600; so 2,080
        assertThat(claims).singleElement().satisfies(claim -> {
            assertThat(claim.playbook().rule()).isEqualTo("DED-DONATION-WHOLE");
            assertThat(claim.allowed()).isEqualTo(Money.of("10000"));
            assertThat(claim.worth()).isEqualTo(Money.of("2080"));
        });
    }

    /** T22-10 and T22-11 as behaviour: the landlord step carries the rule's threshold in rupees. */
    @Test
    void claimPlaybooks_whenTheYearsClaimsAreServed_fillEveryFigureFromItsRule() {
        // When
        List<ClaimPlaybook> playbooks = CALCULATOR.claimPlaybooks(TY_2026_27);

        // Then — SAL-HOUSE-RENT-LANDLORD-PAN.exceeds 1,00,000; HP-OWN-RESIDENCE-INTEREST-CAP 2,00,000 and 30,000
        // (its TY 2026-27 version's definition)
        assertThat(playbooks).hasSize(14);
        ClaimPlaybook houseRent = playbook(playbooks, "SAL-HOUSE-RENT-ALLOWANCE");
        assertThat(houseRent.plainName()).isEqualTo("House Rent Allowance");
        assertThat(houseRent.guidance().steps().get(1).instruction())
                .isEqualTo("If your rent for the year is more than ₹1,00,000, get your landlord's name, address and PAN.");
        assertThat(houseRent.basis().provision2025().reference()).contains("s.11(1) with Sch. III Table Sl. 11; s.392(5)(b)");
        assertThat(playbook(playbooks, "HP-OWN-RESIDENCE-INTEREST-CAP").guidance().steps().get(1).instruction())
                .contains("up to ₹2,00,000 where", "up to ₹30,000 otherwise", "never more than ₹2,00,000");
        assertThat(playbooks.stream().flatMap(ClaimPricingTest::texts)).noneMatch(text -> text.contains("{{"));
    }

    private static ClaimPlaybook playbook(List<ClaimPlaybook> playbooks, String rule) {
        return playbooks.stream().filter(playbook -> playbook.rule().equals(rule)).findFirst().orElseThrow();
    }

    private static Stream<String> texts(ClaimPlaybook playbook) {
        ClaimPlaybook.Guidance guidance = playbook.guidance();
        return Stream.of(guidance.appliesTo().stream(),
                guidance.steps().stream().flatMap(step -> Stream.concat(Stream.of(step.instruction()), step.produces().stream())),
                guidance.askWhom().stream().map(ClaimPlaybook.Request::askFor),
                guidance.evidence().stream().flatMap(item -> Stream.of(item.name(), item.closedBy())),
                guidance.mistakes().stream()).flatMap(texts -> texts);
    }
}
