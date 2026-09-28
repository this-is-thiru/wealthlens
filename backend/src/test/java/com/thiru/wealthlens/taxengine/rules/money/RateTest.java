package com.thiru.wealthlens.taxengine.rules.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** See {@link MoneyTest} for why money is asserted with AssertJ rather than MoneyAssert (D-010). */
class RateTest {

    @Test
    void of_whenApplyingAWholePercentage_isExact() {
        assertThat(Rate.percent("30").of(Money.of("100"))).isEqualTo(Money.of("30"));
    }

    @Test
    void of_whenApplyingAFractionalPercentage_isExact() {
        assertThat(Rate.percent("12.5").of(Money.of("100"))).isEqualTo(Money.of("12.50"));
    }

    /**
     * The health and education cess, on the figures at {@code 08-golden-tests.md} T2-1: four per
     * cent of ₹10,80,000 is ₹43,200. A sourced case rather than an invented one, so this test also
     * pins the arithmetic the golden tests will later depend on.
     */
    @Test
    void of_whenApplyingTheCessRate_matchesTheGoldenCase() {
        assertThat(Rate.percent("4").of(Money.of("1080000"))).isEqualTo(Money.of("43200"));
    }

    @Test
    void of_whenTheProductHasSubPaiseDigits_roundsToPaiseOnce() {
        // 1234.56 x 0.30 = 370.368, which is 370.37 at paise scale, HALF_UP.
        assertThat(Rate.percent("30").of(Money.of("1234.56"))).isEqualTo(Money.of("370.37"));
    }

    @Test
    void of_whenRateIsZero_isZero() {
        assertThat(Rate.ZERO.of(Money.of("999999"))).isEqualTo(Money.ZERO);
    }

    @Test
    void of_whenBaseIsNegative_keepsTheSign() {
        assertThat(Rate.percent("30").of(Money.of("-100"))).isEqualTo(Money.of("-30"));
    }

    @Test
    void asPercentLabel_whenRendered_readsAsTheStatuteWritesIt() {
        assertThat(Rate.percent("30").asPercentLabel()).isEqualTo("30%");
        assertThat(Rate.percent("12.5").asPercentLabel()).isEqualTo("12.5%");
        assertThat(Rate.percent("4").asPercentLabel()).isEqualTo("4%");
        assertThat(Rate.ZERO.asPercentLabel()).isEqualTo("0%");
    }

    @Test
    void compareTo_whenRatesDiffer_ordersByFraction() {
        assertThat(Rate.percent("5")).isLessThan(Rate.percent("30"));
        assertThat(Rate.percent("30")).isEqualByComparingTo(Rate.percent("30"));
    }

    @Test
    void construction_whenFractionIsNull_isRejected() {
        assertThatThrownBy(() -> new Rate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("may not be null");
    }

    @Test
    void ofShare_whenAMonthOfAYear_dividesOnceAndRoundsOnce() {
        // 28,20,000 × 9% ÷ 12 = 21,150
        assertThat(Rate.percent("9").ofShare(Money.of("2820000"), 1, 12)).isEqualTo(Money.of("21150"));
        // 0.62 × 9% ÷ 12 = 0.00465 → 0.00 once; rounding at 9% first (0.06) would give 0.01
        assertThat(Rate.percent("9").ofShare(Money.of("0.62"), 1, 12)).isEqualTo(Money.ZERO);
    }

    @Test
    void ofShare_whenTheWholeIsNil_isRefused() {
        assertThatThrownBy(() -> Rate.percent("9").ofShare(Money.of("100"), 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
