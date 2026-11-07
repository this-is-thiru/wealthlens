package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** A home occupied for a period, as entered for HRA (S4 r.279(2)(a); D-099). Figures are fixtures. */
class TenancyTest {

    private static final DateRange WHOLE_YEAR = DateRange.between(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));

    @Test
    void construct_whenComplete_carriesEachFact() {
        // When
        Tenancy tenancy = new Tenancy(WHOLE_YEAR, "BENGALURU", false, Money.of("360000"));

        // Then
        assertThat(tenancy.location()).isEqualTo("BENGALURU");
        assertThat(tenancy.ownedByAssessee()).isFalse();
        assertThat(tenancy.rentPaid()).isEqualTo(Money.of("360000"));
    }

    @Test
    void construct_whenAFactIsMissing_isRefused() {
        assertThatThrownBy(() -> new Tenancy(null, "BENGALURU", false, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tenancy(WHOLE_YEAR, " ", false, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tenancy(WHOLE_YEAR, "BENGALURU", false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenRentIsNegative_isRefused() {
        assertThatThrownBy(() -> new Tenancy(WHOLE_YEAR, "BENGALURU", false, Money.of("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rent");
    }
}
