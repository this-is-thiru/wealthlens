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

/** A sum received under a life policy (S2 Schedule II Sl. 2, s.92(2)(l); D-205). Figures are fixtures. */
class LifePolicyReceiptTest {

    private static LifePolicyReceipt receipt(String received, String premium, String sumAssured, String unused) {
        return new LifePolicyReceipt(Money.of(received), LocalDate.of(2015, 6, 1), false, false, Money.of(premium),
                Money.of(sumAssured), false, false, true, TaxYearId.of(2015), TaxYearId.of(2024), Money.of(unused));
    }

    @Test
    void construct_whenEveryFactIsGiven_carriesEachFact() {
        // When
        LifePolicyReceipt maturity = receipt("1000000", "70000", "600000", "300000");

        // Then
        assertThat(maturity.highestAnnualPremium()).isEqualTo(Money.of("70000"));
        assertThat(maturity.premiumsNotDeductedOrUsed()).isEqualTo(Money.of("300000"));
    }

    @Test
    void construct_whenAFigureIsNegative_isRefused() {
        assertThatThrownBy(() -> receipt("-1", "70000", "600000", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> receipt("100", "70000", "600000", "-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenTheSumAssuredIsNil_isRefused() {
        assertThatThrownBy(() -> receipt("100", "70000", "0", "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum assured");
    }

    @Test
    void construct_whenTheIssueDateIsMissing_isRefused() {
        assertThatThrownBy(() -> new LifePolicyReceipt(Money.ZERO, null, false, false, Money.ZERO, Money.of("1"), false,
                        false, true, TaxYearId.of(2015), TaxYearId.of(2024), Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenThePremiumYearsRunBackwards_isRefused() {
        assertThatThrownBy(() -> new LifePolicyReceipt(Money.ZERO, LocalDate.of(2023, 4, 1), false, false, Money.ZERO,
                        Money.of("1"), false, false, true, TaxYearId.of(2030), TaxYearId.of(2029), Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("premium years");
    }

    @Test
    void payableIn_whenTheYearIsOutsideThePremiumYears_isFalseAndAtEitherEndTrue() {
        // Given — premiums payable 2015-16 to 2024-25
        LifePolicyReceipt receipt = new LifePolicyReceipt(Money.ZERO, LocalDate.of(2015, 6, 1), false, false, Money.ZERO,
                Money.of("1"), false, false, false, TaxYearId.of(2015), TaxYearId.of(2024), Money.ZERO);

        // When / Then
        assertThat(receipt.payableIn(2014)).isFalse();
        assertThat(receipt.payableIn(2015)).isTrue();
        assertThat(receipt.payableIn(2024)).isTrue();
        assertThat(receipt.payableIn(2025)).isFalse();
    }

    /** Each constructor argument made bad on its own (W-240b). */
    static Stream<Arguments> refusals() {
        LocalDate issued = LocalDate.of(2015, 6, 1);
        TaxYearId from = TaxYearId.of(2015);
        Money sum = Money.of("1");
        String years = "A life policy receipt needs the policy's issue date and premium years";
        String figures = "A life policy receipt needs every figure, none negative";
        return Stream.of(
                refusal("premiumFrom null", () -> new LifePolicyReceipt(Money.ZERO, issued, false, false, Money.ZERO, sum,
                        false, false, false, null, from, Money.ZERO), years),
                refusal("premiumTo null", () -> new LifePolicyReceipt(Money.ZERO, issued, false, false, Money.ZERO, sum,
                        false, false, false, from, null, Money.ZERO), years),
                refusal("received null", () -> new LifePolicyReceipt(null, issued, false, false, Money.ZERO, sum, false,
                        false, false, from, from, Money.ZERO), figures),
                refusal("received negative", () -> new LifePolicyReceipt(Money.of("-1"), issued, false, false, Money.ZERO,
                        sum, false, false, false, from, from, Money.ZERO), figures));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
