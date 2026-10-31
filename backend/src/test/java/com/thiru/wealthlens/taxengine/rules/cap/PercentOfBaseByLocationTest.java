package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A percentage of a base chosen by where the accommodation is (S4 r.279(1)(c) Table; r.2A(c); D-099). The city
 * list is a fixture of two; the salary is the rulebook's R9 example.
 */
class PercentOfBaseByLocationTest {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId LOCATION = new BaseId("LOCATION");
    private static final PercentOfBaseByLocation BY_CITY = new PercentOfBaseByLocation(SALARY, LOCATION,
            List.of(new PercentOfBaseByLocation.Band(Set.of("MUMBAI", "BENGALURU"), Rate.percent("50"))),
            Rate.percent("40"));

    private static CapContext at(String location) {
        return new CapContext(Map.of(SALARY, Money.of("1320000")), Map.of(), Map.of(), Map.of(LOCATION, location));
    }

    @Test
    void apply_whenTheLocationIsListed_takesItsBandsPercentage() {
        // When — 50% × 13,20,000 = 6,60,000
        CapResult result = BY_CITY.apply(at("BENGALURU"));

        // Then
        assertThat(result.value()).isEqualTo(Money.of("660000"));
        assertThat(result.limitedBy()).contains("BENGALURU");
    }

    @Test
    void apply_whenAnyOtherPlace_takesTheOtherwisePercentage() {
        // When — 40% × 13,20,000 = 5,28,000
        CapResult result = BY_CITY.apply(at(PercentOfBaseByLocation.ANY_OTHER_PLACE));

        // Then
        assertThat(result.value()).isEqualTo(Money.of("528000"));
    }

    @Test
    void apply_whenTheLocationIsNeitherListedNorAnyOtherPlace_isRefusedListingTheKnown() {
        // Then — a misspelling must not fall silently into the lower band
        assertThatThrownBy(() -> BY_CITY.apply(at("BANGALORE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BANGALORE")
                .hasMessageContaining("BENGALURU")
                .hasMessageContaining(PercentOfBaseByLocation.ANY_OTHER_PLACE);
    }

    @Test
    void apply_whenNoLocationIsInTheContext_isRefused() {
        assertThatThrownBy(() -> BY_CITY.apply(new CapContext(Map.of(SALARY, Money.of("1320000")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LOCATION");
    }

    @Test
    void construct_whenACityIsInTwoBands_isRefused() {
        assertThatThrownBy(() -> new PercentOfBaseByLocation(SALARY, LOCATION, List.of(
                        new PercentOfBaseByLocation.Band(Set.of("MUMBAI"), Rate.percent("50")),
                        new PercentOfBaseByLocation.Band(Set.of("MUMBAI"), Rate.percent("45"))),
                        Rate.percent("40")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MUMBAI");
    }

    @Test
    void construct_whenABandNamesAnyOtherPlace_isRefused() {
        assertThatThrownBy(() -> new PercentOfBaseByLocation(SALARY, LOCATION, List.of(
                        new PercentOfBaseByLocation.Band(Set.of(PercentOfBaseByLocation.ANY_OTHER_PLACE),
                                Rate.percent("50"))),
                        Rate.percent("40")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
