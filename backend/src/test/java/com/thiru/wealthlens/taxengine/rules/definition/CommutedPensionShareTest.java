package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import org.junit.jupiter.api.Test;

/**
 * The share of a pension whose commuted value may be deducted (S2 s.19(1) Table Sl. 8; 1961 s.10(10A)(ii); D-103):
 * one-third where gratuity is also received, one-half otherwise. Worked by hand.
 */
class CommutedPensionShareTest {

    private static final CommutedPensionShare THIRD_OR_HALF = new CommutedPensionShare(3, 2);

    @Test
    void limit_whenGratuityIsAlsoReceived_isOneThird() {
        // 9,00,000 ÷ 3
        assertThat(THIRD_OR_HALF.limit(Money.of("900000"), true)).isEqualTo(Money.of("300000"));
    }

    @Test
    void limit_whenNoGratuity_isOneHalf() {
        // 9,00,000 ÷ 2
        assertThat(THIRD_OR_HALF.limit(Money.of("900000"), false)).isEqualTo(Money.of("450000"));
    }

    @Test
    void limit_whenTheThirdIsNotWhole_roundsOnceToPaise() {
        // 1,00,000 ÷ 3 = 33,333.333… → 33,333.33
        assertThat(THIRD_OR_HALF.limit(Money.of("100000"), true)).isEqualTo(Money.of("33333.33"));
    }

    @Test
    void constructor_whenAShareIsNotAFraction_isRefused() {
        assertThatThrownBy(() -> new CommutedPensionShare(1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommutedPensionShare(3, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
