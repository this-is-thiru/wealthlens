package com.thiru.wealthlens.taxengine.rules.definition;

import static com.thiru.wealthlens.taxengine.rules.definition.TransportDisability.BLIND;
import static com.thiru.wealthlens.taxengine.rules.definition.TransportDisability.DEAF_AND_DUMB;
import static com.thiru.wealthlens.taxengine.rules.definition.TransportDisability.ORTHOPAEDIC_LOWER_EXTREMITIES;
import static com.thiru.wealthlens.taxengine.rules.definition.TransportDisability.ORTHOPAEDIC_UPPER_EXTREMITIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A disabled employee's monthly transport limit (D-102). The amounts are the two years' as read — r.2BB(2) Table
 * Sl. 11 and S4 r.280(2) Table Sl. 10 — and the sums are worked by hand.
 */
class DisabledTransportLimitTest {

    private static final DisabledTransportLimit TY_2025 = new DisabledTransportLimit(
            Set.of(BLIND, DEAF_AND_DUMB, ORTHOPAEDIC_LOWER_EXTREMITIES), Money.of("3200"), Optional.empty(), false);

    private static final DisabledTransportLimit TY_2026 = new DisabledTransportLimit(
            EnumSet.allOf(TransportDisability.class), Money.of("15000"), Optional.of(Money.of("8000")), true);

    @Test
    void admits_whenUpperExtremitiesIn2025_isFalse() {
        // r.2BB(2) names "disability of lower extremities" only
        assertThat(TY_2025.admits(ORTHOPAEDIC_UPPER_EXTREMITIES)).isFalse();
        assertThat(TY_2025.admits(BLIND)).isTrue();
        assertThat(TY_2026.admits(ORTHOPAEDIC_UPPER_EXTREMITIES)).isTrue();
    }

    @Test
    void monthlyLimit_whenFlat_isTheOneAmount() {
        assertThat(TY_2025.monthlyLimit(Optional.empty(), Optional.empty())).isEqualTo(Money.of("3200"));
    }

    @Test
    void monthlyLimit_whenMetroWithDearnessAllowance_addsIt() {
        // 15,000 + 1,500
        assertThat(TY_2026.monthlyLimit(Optional.of(CommutingCity.METRO_CITY), Optional.of(Money.of("1500"))))
                .isEqualTo(Money.of("16500"));
    }

    @Test
    void monthlyLimit_whenOtherCityWithoutDearnessAllowance_isTheOtherAmount() {
        assertThat(TY_2026.monthlyLimit(Optional.of(CommutingCity.OTHER_CITY), Optional.empty()))
                .isEqualTo(Money.of("8000"));
    }

    @Test
    void monthlyLimit_whenTheCityIsNeededButMissing_isRefused() {
        assertThatThrownBy(() -> TY_2026.monthlyLimit(Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metro");
    }

    @Test
    void monthlyLimit_whenAFactTheYearDoesNotReadIsGiven_isRefusedRatherThanIgnored() {
        assertThatThrownBy(() -> TY_2025.monthlyLimit(Optional.of(CommutingCity.METRO_CITY), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("city");
        assertThatThrownBy(() -> TY_2025.monthlyLimit(Optional.empty(), Optional.of(Money.of("1500"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dearness allowance");
    }

    @Test
    void constructor_whenNoConditions_isRefused() {
        assertThatThrownBy(() -> new DisabledTransportLimit(Set.of(), Money.of("3200"), Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
