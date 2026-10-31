package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

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
}
