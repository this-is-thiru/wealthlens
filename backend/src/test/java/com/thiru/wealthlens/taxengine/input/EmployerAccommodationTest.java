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
    void construct_whenOwnedWithItsBand_carriesIt() {
        // When
        EmployerAccommodation owned = new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED, YEAR,
                Optional.of(PopulationBand.ABOVE_FORTY_LAKH), Optional.empty(), Money.of("24000"), Optional.empty(),
                TaxYearId.of(2026), Optional.empty());

        // Then
        assertThat(owned.band()).contains(PopulationBand.ABOVE_FORTY_LAKH);
        assertThat(owned.rentPaid()).isEqualTo(Money.of("24000"));
    }

    @Test
    void construct_whenOwnedWithoutItsBand_isRefused() {
        // Table I Sl. 2(a): the percentage turns on the city's 2011-census population
        assertThatThrownBy(() -> new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_OWNED, YEAR,
                        Optional.empty(), Optional.empty(), Money.ZERO, Optional.empty(), TaxYearId.of(2026), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("population");
    }

    @Test
    void construct_whenLeasedWithoutTheEmployersCost_isRefused() {
        assertThatThrownBy(() -> new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_LEASED, YEAR,
                        Optional.empty(), Optional.empty(), Money.ZERO, Optional.empty(), TaxYearId.of(2026), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cost");
    }

    @Test
    void construct_whenABandIsGivenForLeasedAccommodation_isRefusedRatherThanIgnored() {
        assertThatThrownBy(() -> new EmployerAccommodation(0, AccommodationProvider.EMPLOYER_LEASED, YEAR,
                        Optional.of(PopulationBand.ANY_OTHER_PLACE), Optional.of(Money.of("180000")), Money.ZERO,
                        Optional.empty(), TaxYearId.of(2026), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("band");
    }

    @Test
    void construct_whenAHotelIsGivenFurniture_isRefused() {
        // Table I Sl. 3: a hotel is valued on its charges; furniture is "Not applicable"
        assertThatThrownBy(() -> new EmployerAccommodation(0, AccommodationProvider.HOTEL, YEAR, Optional.empty(),
                        Optional.of(Money.of("90000")), Money.ZERO,
                        Optional.of(Furniture.owned(Money.of("200000"), Money.ZERO)), TaxYearId.of(2026), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("furniture");
    }

    @Test
    void furniture_whenBothCostAndHireChargesAreGiven_isRefused() {
        assertThatThrownBy(() -> new Furniture(Optional.of(Money.of("200000")), Optional.of(Money.of("30000")),
                        Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
