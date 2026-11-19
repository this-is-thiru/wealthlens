package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Each head's loss brought forward and to carry forward is an amount, nil or more, and both maps are always given
 * (W-090c1b2, D-528; proven in W-265, D-531).
 */
class LossesAcrossYearsTest {

    @Test
    void constructor_whenEitherMapIsNull_refuses() {
        assertThatThrownBy(() -> new LossesAcrossYears(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("LossesAcrossYears needs both maps; pass empty ones");
        assertThatThrownBy(() -> new LossesAcrossYears(Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("LossesAcrossYears needs both maps; pass empty ones");
    }

    @Test
    void constructor_whenALossIsNegative_refusesNamingTheHead() {
        // Given — a loss of −₹1 under house property, to carry forward
        Map<HeadCode, Money> negative = Map.of(HeadCode.HOUSE_PROPERTY, Money.of("-1"));

        // When / Then
        assertThatThrownBy(() -> new LossesAcrossYears(Map.of(), negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to carry forward is nil or more, but under HOUSE_PROPERTY was");
    }

    @Test
    void constructor_whenGivenAMutableMap_keepsACopy() {
        // Given
        Map<HeadCode, Money> broughtForward = new HashMap<>(Map.of(HeadCode.CAPITAL_GAINS, Money.of("40000")));

        // When
        LossesAcrossYears losses = new LossesAcrossYears(broughtForward, Map.of());
        broughtForward.put(HeadCode.CAPITAL_GAINS, Money.ZERO);

        // Then — the caller's later change does not reach the value
        assertThat(losses.broughtForward()).containsEntry(HeadCode.CAPITAL_GAINS, Money.of("40000"));
    }
}
