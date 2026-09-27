package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The amount a labour statute entitles, as the employer computed it (D-109). Fixtures. */
class StatutoryEntitlementTest {

    @Test
    void construct_whenTheFactsAreGiven_carriesThem() {
        // When
        StatutoryEntitlement entitlement = new StatutoryEntitlement(LocalDate.of(2025, 12, 15), Money.of("600000"));

        // Then
        assertThat(entitlement.separatedOn()).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(entitlement.amountUnderStatute()).isEqualTo(Money.of("600000"));
    }

    @Test
    void construct_whenTheAmountIsNegative_isRefused() {
        assertThatThrownBy(() -> new StatutoryEntitlement(LocalDate.of(2025, 12, 15), Money.of("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}
