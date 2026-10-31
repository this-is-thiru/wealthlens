package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.definition.CommutingCity;
import com.thiru.wealthlens.taxengine.rules.definition.TransportDisability;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A disabled employee's transport allowance claim, as entered (D-102). Figures are fixtures. */
class DisabledTransportClaimTest {

    @Test
    void construct_whenTheFactsAreGiven_carriesThem() {
        // When
        DisabledTransportClaim claim = new DisabledTransportClaim(TransportDisability.BLIND, 12,
                Optional.of(CommutingCity.METRO_CITY), Optional.of(Money.of("1500")));

        // Then
        assertThat(claim.condition()).isEqualTo(TransportDisability.BLIND);
        assertThat(claim.monthsCovered()).isEqualTo(12);
        assertThat(claim.city()).contains(CommutingCity.METRO_CITY);
        assertThat(claim.dearnessAllowanceThereon()).contains(Money.of("1500"));
    }

    @Test
    void construct_whenMonthsOutsideTheYear_isRefused() {
        assertThatThrownBy(() -> new DisabledTransportClaim(TransportDisability.BLIND, 0, Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 to 12");
        assertThatThrownBy(() -> new DisabledTransportClaim(TransportDisability.BLIND, 13, Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenTheDearnessAllowanceIsNegative_isRefused() {
        assertThatThrownBy(() -> new DisabledTransportClaim(TransportDisability.BLIND, 12, Optional.empty(),
                        Optional.of(Money.of("-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void construct_whenNoCondition_isRefused() {
        assertThatThrownBy(() -> new DisabledTransportClaim(null, 12, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
