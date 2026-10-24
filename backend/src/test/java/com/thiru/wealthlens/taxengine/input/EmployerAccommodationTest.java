package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Accommodation an employer provides, as entered (S4 r.15(2) Table I; Rules 1962 r.3(1); D-111). Fixtures. */
class EmployerAccommodationTest {

    private static final DateRange YEAR = DateRange.between(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));

    @Test
    void construct_whenOwnedWithItsCityPopulation_carriesIt() {
        // When
        EmployerAccommodation owned = new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED, YEAR,
                Optional.of(5_000_000), Optional.empty(), Money.of("24000"), Optional.empty(),
                TaxYearId.of(2026), Optional.empty());

        // Then
        assertThat(owned.cityPopulation()).contains(5_000_000);
        assertThat(owned.rentPaid()).isEqualTo(Money.of("24000"));
    }

    @Test
    void construct_whenTheCityPopulationIsNotPositive_isRefused() {
        assertThatThrownBy(() -> new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED, YEAR,
                        Optional.of(0), Optional.empty(), Money.ZERO, Optional.empty(), TaxYearId.of(2026),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void furniture_whenBothCostAndHireChargesAreGiven_isRefused() {
        assertThatThrownBy(() -> new Furniture(Optional.of(Money.of("200000")), Optional.of(Money.of("30000")),
                        Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
