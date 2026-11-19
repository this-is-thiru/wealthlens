package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import org.junit.jupiter.api.Test;

/** The one-way seam from canonical {@code double} rupees to exact {@code Money} (D-240). */
class MoneyBridgeTest {

    @Test
    void fromTMoney_whenTheAmountIsCanonical_widensItExactly() {
        // When / Then — TMoney's own example, stored as 145.38
        assertThat(MoneyBridge.fromTMoney(145.38)).isEqualTo(Money.of("145.38"));
    }

    @Test
    void fromTMoney_whenTheAmountIsALoss_keepsItNegative() {
        // When / Then
        assertThat(MoneyBridge.fromTMoney(-2500.0)).isEqualTo(Money.of("-2500.00"));
    }

    @Test
    void fromTMoney_whenTheAmountIsZero_givesZero() {
        // When / Then
        assertThat(MoneyBridge.fromTMoney(0.0)).isEqualTo(Money.ZERO);
    }

    @Test
    void fromTMoney_whenTheAmountSkippedTMoney_isRefusedRatherThanRounded() {
        // When / Then — Money alone would round this to 145.38 without a word
        assertThatThrownBy(() -> MoneyBridge.fromTMoney(145.37999999999997))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not canonical to paise");
    }

    @Test
    void fromTMoney_whenTheAmountIsNotANumber_isRefused() {
        // When / Then
        assertThatThrownBy(() -> MoneyBridge.fromTMoney(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN");
        assertThatThrownBy(() -> MoneyBridge.fromTMoney(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Infinity");
    }

    @Test
    void toTMoney_whenCalled_isRefused() {
        // When / Then — nothing flows back
        assertThatThrownBy(() -> MoneyBridge.toTMoney(Money.of("1")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("exact by construction");
    }
}
