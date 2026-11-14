package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** An old capped policy, claimed exempt earlier (CBDT Circulars No. 2/2022, 15/2023; D-207). Figures are fixtures. */
class ClaimedPolicyTest {

    @Test
    void payableIn_whenTheYearIsWithinItsPremiumYears_isTrue() {
        // When — premiums 2023-24 to 2032-33
        ClaimedPolicy old = new ClaimedPolicy(LocalDate.of(2023, 4, 1), false, Money.of("450000"), TaxYearId.of(2023),
                TaxYearId.of(2032));

        // Then
        assertThat(old.payableIn(2023)).isTrue();
        assertThat(old.payableIn(2032)).isTrue();
        assertThat(old.payableIn(2033)).isFalse();
    }

    @Test
    void construct_whenThePremiumYearsRunBackwardsOrAFactIsMissing_isRefused() {
        assertThatThrownBy(() -> new ClaimedPolicy(LocalDate.of(2023, 4, 1), false, Money.of("1"), TaxYearId.of(2030),
                        TaxYearId.of(2029)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClaimedPolicy(LocalDate.of(2023, 4, 1), false, null, TaxYearId.of(2023),
                        TaxYearId.of(2032)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payableIn_whenTheYearIsOutsideThePremiumYears_isFalseAndAtEitherEndTrue() {
        // Given — premiums payable 2023-24 to 2025-26
        ClaimedPolicy policy = new ClaimedPolicy(LocalDate.of(2023, 4, 1), false, Money.of("50000"), TaxYearId.of(2023),
                TaxYearId.of(2025));

        // When / Then
        assertThat(policy.payableIn(2022)).isFalse();
        assertThat(policy.payableIn(2023)).isTrue();
        assertThat(policy.payableIn(2025)).isTrue();
        assertThat(policy.payableIn(2026)).isFalse();
    }

    /** Each constructor argument made bad on its own (W-240b). */
    static Stream<Arguments> refusals() {
        LocalDate issued = LocalDate.of(2023, 4, 1);
        Money premium = Money.of("50000");
        TaxYearId from = TaxYearId.of(2023);
        String needs = "A claimed policy needs its issue date, annual premium and premium years";
        return Stream.of(
                refusal("issuedOn null", () -> new ClaimedPolicy(null, false, premium, from, from), needs),
                refusal("premiumFrom null", () -> new ClaimedPolicy(issued, false, premium, null, from), needs),
                refusal("premiumTo null", () -> new ClaimedPolicy(issued, false, premium, from, null), needs),
                refusal("annualPremium negative", () -> new ClaimedPolicy(issued, false, Money.of("-1"), from, from),
                        "A claimed policy's premium may not be negative"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
