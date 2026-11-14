package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    /** Each constructor argument made bad on its own, the rest a valid owned flat (W-240a). */
    static Stream<Arguments> refusals() {
        AccommodationProvider owned = AccommodationProvider.EMPLOYER_OWNED;
        Optional<Integer> city = Optional.of(5_000_000);
        TaxYearId year = TaxYearId.of(2026);
        Optional<Money> negative = Optional.of(Money.of("-1"));
        String needs = "Employer accommodation needs its provider, period, rent paid and first year";
        String amounts = "Accommodation amounts may not be negative";
        return Stream.of(
                refusal("provider null", () -> new EmployerAccommodation(0, null, YEAR, city, Optional.empty(),
                        Money.ZERO, Optional.empty(), year, Optional.empty()), needs),
                refusal("occupied null", () -> new EmployerAccommodation(0, owned, null, city, Optional.empty(),
                        Money.ZERO, Optional.empty(), year, Optional.empty()), needs),
                refusal("cityPopulation null", () -> new EmployerAccommodation(0, owned, YEAR, null, Optional.empty(),
                        Money.ZERO, Optional.empty(), year, Optional.empty()), needs),
                refusal("employerCost null", () -> new EmployerAccommodation(0, owned, YEAR, city, null, Money.ZERO,
                        Optional.empty(), year, Optional.empty()), needs),
                refusal("rentPaid null", () -> new EmployerAccommodation(0, owned, YEAR, city, Optional.empty(), null,
                        Optional.empty(), year, Optional.empty()), needs),
                refusal("furniture null", () -> new EmployerAccommodation(0, owned, YEAR, city, Optional.empty(),
                        Money.ZERO, null, year, Optional.empty()), needs),
                refusal("firstProvidedIn null", () -> new EmployerAccommodation(0, owned, YEAR, city, Optional.empty(),
                        Money.ZERO, Optional.empty(), null, Optional.empty()), needs),
                refusal("firstYearValue null", () -> new EmployerAccommodation(0, owned, YEAR, city, Optional.empty(),
                        Money.ZERO, Optional.empty(), year, null), needs),
                refusal("employment negative", () -> new EmployerAccommodation(-1, owned, YEAR, city, Optional.empty(),
                        Money.ZERO, Optional.empty(), year, Optional.empty()),
                        "An employment position may not be negative, but was -1"),
                refusal("rentPaid negative", () -> new EmployerAccommodation(0, owned, YEAR, city, Optional.empty(),
                        Money.of("-1"), Optional.empty(), year, Optional.empty()), amounts),
                refusal("employerCost negative", () -> new EmployerAccommodation(0, owned, YEAR, city, negative,
                        Money.ZERO, Optional.empty(), year, Optional.empty()), amounts),
                refusal("firstYearValue negative", () -> new EmployerAccommodation(0, owned, YEAR, city,
                        Optional.empty(), Money.ZERO, Optional.empty(), year, negative), amounts));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
