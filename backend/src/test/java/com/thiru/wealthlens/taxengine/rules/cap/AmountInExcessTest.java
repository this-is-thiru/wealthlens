package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * "The amount by which … exceeds …" (S4 r.279(1)(b); r.2A(b); D-099): HRA's second limb, rent over one-tenth of
 * salary. The figures are the rulebook's R9 worked example, recomputed by hand.
 */
class AmountInExcessTest {

    private static final BaseId RENT_PAID = new BaseId("RENT_PAID");
    private static final BaseId SALARY = new BaseId("SALARY");
    private static final AmountInExcess RENT_OVER_A_TENTH =
            new AmountInExcess(RENT_PAID, new PercentOfBase(Rate.percent("10"), SALARY));

    @Test
    void apply_whenRentExceedsTheThreshold_isTheExcess() {
        // When — 3,60,000 − 10% × 13,20,000 = 3,60,000 − 1,32,000 = 2,28,000
        CapResult result = RENT_OVER_A_TENTH.apply(
                new CapContext(Map.of(RENT_PAID, Money.of("360000"), SALARY, Money.of("1320000"))));

        // Then
        assertThat(result.value()).isEqualTo(Money.of("228000"));
    }

    @Test
    void apply_whenRentIsBelowTheThreshold_isNilNotNegative() {
        // When — 1,00,000 against 1,32,000: nothing exceeds
        CapResult result = RENT_OVER_A_TENTH.apply(
                new CapContext(Map.of(RENT_PAID, Money.of("100000"), SALARY, Money.of("1320000"))));

        // Then
        assertThat(result.value()).isEqualTo(Money.ZERO);
    }

    @Test
    void apply_whenTheAmountIsNotInTheContext_isRefused() {
        assertThatThrownBy(() -> RENT_OVER_A_TENTH.apply(new CapContext(Map.of(SALARY, Money.of("1320000")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RENT_PAID");
    }

    @Test
    void construct_withoutAThreshold_isRefused() {
        assertThatThrownBy(() -> new AmountInExcess(RENT_PAID, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
