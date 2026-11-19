package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The gift thresholds (S2 s.92(2)(m); 1961 s.56(2)(x); D-201, D-352): ₹50,000 for every limb, and for immovable
 * property the higher of ₹50,000 and 10% of the consideration. Worked by hand.
 */
class GiftThresholdTest {

    private static final GiftThreshold IMMOVABLE =
            new GiftThreshold(Money.of("50000"), Optional.of(Rate.percent("10")), GiftAggregation.EACH_RECEIPT);

    private static final GiftThreshold NO_SHARE =
            new GiftThreshold(Money.of("50000"), Optional.empty(), GiftAggregation.EACH_RECEIPT);

    @Test
    void charged_whenNothingWasPaidAndTheValueEqualsTheAmount_isNil() {
        // Given — "exceeds", so equal is not
        // When / Then
        assertThat(NO_SHARE.charged(Money.ZERO, Money.of("50000"))).isEqualTo(Money.ZERO);
        assertThat(NO_SHARE.charged(Money.ZERO, Money.of("50000.01"))).isEqualTo(Money.of("50000.01"));
    }

    @Test
    void charged_whenTheExcessIsWithinTenPerCent_isNil() {
        // Given — 54,00,000 − 50,00,000 = 4,00,000, not more than max(50,000, 5,00,000)
        // When / Then
        assertThat(IMMOVABLE.charged(Money.of("5000000"), Money.of("5400000"))).isEqualTo(Money.ZERO);
    }

    @Test
    void charged_whenTheExcessIsBeyondTenPerCent_isTheExcess() {
        // Given — 56,00,000 − 50,00,000 = 6,00,000 > 5,00,000
        // When / Then
        assertThat(IMMOVABLE.charged(Money.of("5000000"), Money.of("5600000"))).isEqualTo(Money.of("600000"));
    }

    @Test
    void charged_whenTheFlatAmountIsHigherThanTheShare_testsAgainstIt() {
        // Given — 3,60,000 − 3,00,000 = 60,000 > max(50,000, 30,000)
        // When / Then
        assertThat(IMMOVABLE.charged(Money.of("300000"), Money.of("360000"))).isEqualTo(Money.of("60000"));
    }

    @Test
    void charged_whenNothingWasPaidForImmovableProperty_isTheWholeStampDutyValue() {
        // When / Then — max(50,000, 10% of nil) = 50,000
        assertThat(IMMOVABLE.charged(Money.ZERO, Money.of("600000"))).isEqualTo(Money.of("600000"));
        assertThat(IMMOVABLE.charged(Money.ZERO, Money.of("50000"))).isEqualTo(Money.ZERO);
    }

    @Test
    void charged_whenTheLimbHasNoShare_testsTheShortfallAgainstTheAmount() {
        // Given — 1,60,000 − 1,00,000 = 60,000 > 50,000; 1,50,000 − 1,00,000 = 50,000 is not
        // When / Then
        assertThat(NO_SHARE.charged(Money.of("100000"), Money.of("160000"))).isEqualTo(Money.of("60000"));
        assertThat(NO_SHARE.charged(Money.of("100000"), Money.of("150000"))).isEqualTo(Money.ZERO);
    }

    @Test
    void constructor_whenAFigureIsNilOrMissing_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new GiftThreshold(Money.ZERO, Optional.empty(), GiftAggregation.YEAR_TOTAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GiftThreshold(Money.of("50000"), null, GiftAggregation.YEAR_TOTAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GiftThreshold(Money.of("50000"), Optional.of(Rate.ZERO),
                GiftAggregation.YEAR_TOTAL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GiftThreshold(Money.of("50000"), Optional.empty(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
