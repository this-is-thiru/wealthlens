package com.thiru.wealthlens.taxengine.optimise;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.PricedClaim;
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
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Whether a claim changes the amount finally payable is measured, not judged by its size (W-121 C1a): s.516 rounds the
 * amount payable to the nearest ₹10, five or more upward ({@code RATE-ROUND-PAYABLE}), so where the bill sits decides it.
 *
 * <p>Worked by hand from the TY 2026-27 old-regime rows, as {@code ClaimPricingTest} states them: basic pay 10,00,000 less
 * the standard deduction 50,000 is 9,50,000; a Schedule XV payment of {@code x} leaves {@code 9,50,000 − x}, taxed
 * 12,500 + 20 % above 5,00,000, so 1,02,500 − 0.2x, with cess at 4 %: a bill of 1,06,600 − 0.208x. Tax at source is set so
 * the balance stays under ₹10,000, where no advance tax is due and so no interest arises
 * ({@code ADV-LIABILITY}: {@code atLeast} 10,000; s.403 – s.406); the return is not yet due, so no fee.
 */
class ClaimFinalAmountTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final TaxCalculator CALCULATOR =
            new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)));

    private static PricedClaim tuition(String paid, String taxAtSource) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment basicPay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1000000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        EnteredFacts entered = EnteredFacts.none().withEmployments(List.of(basicPay))
                .withQualifyingPayments(List.of(new QualifyingPayment(new ComponentCode("TUITION_FEES"), Money.of(paid))));
        FilingFacts filing = new FilingFacts(LocalDate.of(2027, 7, 31), Optional.empty(), List.of(), Money.of(taxAtSource),
                List.of(), Map.of(), Optional.empty());
        EngineInputs inputs = new EngineInputs(TY_2026_27, new RegimeCode("FIRST_SCHEDULE_RATES"), plain.temporal(), entered,
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), filing);
        return CALCULATOR.priceClaims(inputs).stream().filter(claim -> claim.playbook().rule().equals("DED-SCHEDULE-XV"))
                .findFirst().orElseThrow();
    }

    @Test
    void priceClaims_whenAClaimWorthMoreThanHalfTheMultipleLandsInTheSameTen_leavesTheFinalAmount() {
        // When — 40 paid: worth 8.32, more than half of 10. Without it, 1,06,600 − 99,996 = 6,604, rounded 6,600; with it,
        // 1,06,591.68 − 99,996 = 6,595.68, paise ignored 6,595, last figure five, rounded up to 6,600
        PricedClaim claim = tuition("40", "99996");

        // Then
        assertThat(claim.worth()).isEqualTo(Money.of("8.32"));
        assertThat(claim.finalAmountWithout()).isEqualTo(Money.of("6600"));
        assertThat(claim.finalAmount()).isEqualTo(Money.of("6600"));
    }

    @Test
    void priceClaims_whenAClaimWorthLessThanHalfTheMultipleCrossesATen_changesTheFinalAmount() {
        // When — 20 paid: worth 4.16, less than half of 10. Without it, 1,06,600 − 99,995 = 6,605, rounded up to 6,610;
        // with it, 1,06,595.84 − 99,995 = 6,600.84, paise ignored 6,600
        PricedClaim claim = tuition("20", "99995");

        // Then
        assertThat(claim.worth()).isEqualTo(Money.of("4.16"));
        assertThat(claim.finalAmountWithout()).isEqualTo(Money.of("6610"));
        assertThat(claim.finalAmount()).isEqualTo(Money.of("6600"));
    }

    @Test
    void priceClaims_whenAnOrdinaryClaimIsMade_changesTheFinalAmountByAboutItsWorth() {
        // When — 10,000 paid: worth 2,080. Without it, 6,605, rounded 6,610; with it, 1,04,520 − 99,995 = 4,525, rounded
        // up to 4,530
        PricedClaim claim = tuition("10000", "99995");

        // Then
        assertThat(claim.worth()).isEqualTo(Money.of("2080"));
        assertThat(claim.finalAmountWithout()).isEqualTo(Money.of("6610"));
        assertThat(claim.finalAmount()).isEqualTo(Money.of("4530"));
    }
}
