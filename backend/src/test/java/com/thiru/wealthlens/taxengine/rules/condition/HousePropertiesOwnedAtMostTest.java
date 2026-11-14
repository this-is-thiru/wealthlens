package com.thiru.wealthlens.taxengine.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** r.164(2)(b)'s "does not own more than two house property", with the limit as data (W-090c1a). */
class HousePropertiesOwnedAtMostTest {

    private static final HousePropertiesOwnedAtMost AT_MOST_TWO = new HousePropertiesOwnedAtMost(2);

    private static ConditionContext owning(int houses) {
        return new ConditionContext(ResidentialStatus.RESIDENT, PersonType.INDIVIDUAL, Map.of(), Map.of())
                .owning(houses);
    }

    @Test
    void holds_whenOwningExactlyTheLimit_holds() {
        assertThat(AT_MOST_TWO.holds(owning(2))).isTrue();
    }

    @Test
    void holds_whenOwningOneMoreThanTheLimit_fails() {
        assertThat(AT_MOST_TWO.holds(owning(3))).isFalse();
    }

    @Test
    void holds_whenOwningNone_holds() {
        assertThat(AT_MOST_TWO.holds(owning(0))).isTrue();
    }

    @Test
    void holds_whenNobodyCountedTheProperties_throws() {
        // Given — a context built without the count; answering nought would pass the limit for anyone
        ConditionContext uncounted =
                new ConditionContext(ResidentialStatus.RESIDENT, PersonType.INDIVIDUAL, Map.of(), Map.of());

        // When / Then
        assertThatThrownBy(() -> AT_MOST_TWO.holds(uncounted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No count of house properties owned");
    }

    @Test
    void constructor_whenTheLimitIsNegative_refuses() {
        assertThatThrownBy(() -> new HousePropertiesOwnedAtMost(-1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nought or more, not -1");
    }

    @Test
    void owning_whenTheCountIsNegative_refuses() {
        assertThatThrownBy(() -> owning(-1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot own -1 house properties");
    }
}
