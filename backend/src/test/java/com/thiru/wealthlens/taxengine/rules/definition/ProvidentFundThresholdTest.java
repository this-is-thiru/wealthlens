package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import org.junit.jupiter.api.Test;

/**
 * The provident fund threshold (S2 Schedule II Sl. 3(a), 4(a); S4 r.277(3)(d); Rules 1962 r.9D; D-198): ₹5,00,000
 * where the employer makes no contribution, ₹2,50,000 otherwise. Worked by hand.
 */
class ProvidentFundThresholdTest {

    private static final ProvidentFundThreshold THRESHOLD = new ProvidentFundThreshold(Money.of("500000"),
            Money.of("250000"));

    @Test
    void excess_whenTheEmployerContributes_isBeyondTheLowerThreshold() {
        // 6,00,000 − 2,50,000 = 3,50,000 (DR 08 T4-4)
        assertThat(THRESHOLD.excess(Money.of("600000"), true)).isEqualTo(Money.of("350000"));
    }

    @Test
    void excess_whenTheEmployerDoesNotContribute_isBeyondTheHigherThreshold() {
        // 6,00,000 − 5,00,000 = 1,00,000
        assertThat(THRESHOLD.excess(Money.of("600000"), false)).isEqualTo(Money.of("100000"));
    }

    @Test
    void excess_whenTheContributionEqualsTheThreshold_isNil() {
        // "exceeds": equal is not
        assertThat(THRESHOLD.excess(Money.of("500000"), false)).isEqualTo(Money.ZERO);
        assertThat(THRESHOLD.excess(Money.of("250000"), true)).isEqualTo(Money.ZERO);
    }

    @Test
    void excess_whenTheContributionIsUnderTheThreshold_isNil() {
        assertThat(THRESHOLD.excess(Money.of("100000"), true)).isEqualTo(Money.ZERO);
    }

    @Test
    void constructor_whenAThresholdIsNilOrMissing_isRefused() {
        assertThatThrownBy(() -> new ProvidentFundThreshold(Money.ZERO, Money.of("250000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProvidentFundThreshold(Money.of("500000"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
