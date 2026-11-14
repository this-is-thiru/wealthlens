package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import org.junit.jupiter.api.Test;

/**
 * The family-pension deduction (S2 s.93(1)(d); 1961 s.57(iia); D-196): one-third of the pension or the limit,
 * whichever is less. Worked by hand.
 */
class FamilyPensionShareTest {

    private static final FamilyPensionShare THIRD_WITHIN_15000 = new FamilyPensionShare(3, Money.of("15000"));

    @Test
    void deduction_whenTheThirdIsUnderTheLimit_isTheThird() {
        // 30,000 ÷ 3 = 10,000, under 15,000
        assertThat(THIRD_WITHIN_15000.deduction(Money.of("30000"))).isEqualTo(Money.of("10000"));
    }

    @Test
    void deduction_whenTheThirdExceedsTheLimit_isTheLimit() {
        // 60,000 ÷ 3 = 20,000, over 15,000
        assertThat(THIRD_WITHIN_15000.deduction(Money.of("60000"))).isEqualTo(Money.of("15000"));
    }

    @Test
    void deduction_whenTheThirdIsNotWhole_roundsOnceToPaise() {
        // 10,000 ÷ 3 = 3,333.333… → 3,333.33
        assertThat(THIRD_WITHIN_15000.deduction(Money.of("10000"))).isEqualTo(Money.of("3333.33"));
    }

    @Test
    void constructor_whenTheShareIsNotAFractionOrTheLimitIsNil_isRefused() {
        assertThatThrownBy(() -> new FamilyPensionShare(1, Money.of("15000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FamilyPensionShare(3, Money.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
