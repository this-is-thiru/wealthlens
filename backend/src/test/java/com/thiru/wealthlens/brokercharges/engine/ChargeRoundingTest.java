package com.thiru.wealthlens.brokercharges.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Rounding is applied once, by the engine, after every other modifier.
 *
 * <p>Brokers do not round uniformly: statutory charges are conventionally taken to the nearest
 * rupee while brokerage and exchange charges carry paise. Getting this wrong shows up as drift
 * against a real contract note, so each policy is pinned at its boundary.
 */
class ChargeRoundingTest {

    @ParameterizedTest(name = "HALF_UP_2: {0} -> {1}")
    @CsvSource({
        "2.9749, 2.97",
        "2.9750, 2.98",
        "2.9751, 2.98",
        "2.9700, 2.97",
        "0.0049, 0.00",
        "0.0050, 0.01",
        "100, 100.00"
    })
    void apply_halfUpToPaise(String input, String expected) {
        assertThat(ChargeRounding.apply(new BigDecimal(input), RoundingPolicy.HALF_UP_2))
                .isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "HALF_UP_0: {0} -> {1}")
    @CsvSource({
        "100.49, 100",
        "100.50, 101",
        "100.51, 101",
        "0.49, 0",
        "0.50, 1"
    })
    void apply_halfUpToRupee(String input, String expected) {
        assertThat(ChargeRounding.apply(new BigDecimal(input), RoundingPolicy.HALF_UP_0))
                .isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "CEILING_2: {0} -> {1}")
    @CsvSource({
        "2.971, 2.98",
        "2.970, 2.97",
        "2.9701, 2.98",
        "0.0001, 0.01"
    })
    void apply_ceilingToPaise(String input, String expected) {
        assertThat(ChargeRounding.apply(new BigDecimal(input), RoundingPolicy.CEILING_2))
                .isEqualByComparingTo(expected);
    }

    @Test
    void apply_whenPolicyIsNone_leavesTheValueUntouched() {
        assertThat(ChargeRounding.apply(new BigDecimal("2.974913"), RoundingPolicy.NONE))
                .isEqualByComparingTo("2.974913");
    }

    @Test
    void apply_whenPolicyIsNull_defaultsToPaise() {
        // A rule that omits a rounding policy gets the sensible default rather than raw precision,
        // since an unrounded amount would otherwise reach the contract note.
        assertThat(ChargeRounding.apply(new BigDecimal("2.9749"), null)).isEqualByComparingTo("2.97");
    }

    @Test
    void apply_whenAmountIsNull_isRejected() {
        assertThatThrownBy(() -> ChargeRounding.apply(null, RoundingPolicy.HALF_UP_2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void apply_neverProducesNegativeZero() {
        // Negative zero compares equal to zero but serialises as "-0.0", which is alarming on a
        // contract note and in a JSON payload.
        assertThat(ChargeRounding.apply(new BigDecimal("-0.0001"), RoundingPolicy.HALF_UP_2).toPlainString())
                .isEqualTo("0.00");
    }
}
