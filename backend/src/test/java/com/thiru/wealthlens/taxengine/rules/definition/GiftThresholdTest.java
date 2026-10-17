package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import org.junit.jupiter.api.Test;

/**
 * The gift thresholds (S2 s.92(2)(m); 1961 s.56(2)(x); D-201): ₹50,000, and for immovable property the higher of
 * ₹50,000 and 10% of the consideration. Worked by hand.
 */
class GiftThresholdTest {

    private static final GiftThreshold THRESHOLD = new GiftThreshold(Money.of("50000"), Rate.percent("10"));

    @Test
    void isExceededBy_whenEqual_isNot() {
        assertThat(THRESHOLD.isExceededBy(Money.of("50000"))).isFalse();
        assertThat(THRESHOLD.isExceededBy(Money.of("50000.01"))).isTrue();
    }

    @Test
    void immovablePropertyCharged_whenTheExcessIsWithinTenPerCent_isNil() {
        // 54,00,000 − 50,00,000 = 4,00,000, not more than max(50,000, 5,00,000)
        assertThat(THRESHOLD.immovablePropertyCharged(Money.of("5000000"), Money.of("5400000"))).isEqualTo(Money.ZERO);
    }

    @Test
    void immovablePropertyCharged_whenTheExcessIsBeyondTenPerCent_isTheExcess() {
        // 56,00,000 − 50,00,000 = 6,00,000 > 5,00,000
        assertThat(THRESHOLD.immovablePropertyCharged(Money.of("5000000"), Money.of("5600000")))
                .isEqualTo(Money.of("600000"));
    }

    @Test
    void immovablePropertyCharged_whenTheFlatAmountIsHigher_testsAgainstIt() {
        // 3,60,000 − 3,00,000 = 60,000 > max(50,000, 30,000)
        assertThat(THRESHOLD.immovablePropertyCharged(Money.of("300000"), Money.of("360000")))
                .isEqualTo(Money.of("60000"));
    }

    @Test
    void immovablePropertyCharged_whenNothingWasPaid_isTheWholeStampDutyValue() {
        assertThat(THRESHOLD.immovablePropertyCharged(Money.ZERO, Money.of("600000"))).isEqualTo(Money.of("600000"));
        assertThat(THRESHOLD.immovablePropertyCharged(Money.ZERO, Money.of("50000"))).isEqualTo(Money.ZERO);
    }

    @Test
    void propertyShortfallCharged_whenTheShortfallExceedsTheThreshold_isTheShortfall() {
        assertThat(THRESHOLD.propertyShortfallCharged(Money.of("100000"), Money.of("160000")))
                .isEqualTo(Money.of("60000"));
        assertThat(THRESHOLD.propertyShortfallCharged(Money.of("100000"), Money.of("150000"))).isEqualTo(Money.ZERO);
    }

    @Test
    void constructor_whenAFigureIsNilOrMissing_isRefused() {
        assertThatThrownBy(() -> new GiftThreshold(Money.ZERO, Rate.percent("10")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GiftThreshold(Money.of("50000"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
