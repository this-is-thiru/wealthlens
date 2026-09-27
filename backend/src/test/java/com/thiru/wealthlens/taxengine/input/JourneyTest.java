package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A journey on leave, as entered for LTA (S4 r.278(1); Rules 1962 r.2B(1); D-100). Figures are fixtures. */
class JourneyTest {

    private static final LocalDate STARTED = LocalDate.of(2026, 5, 10);

    @Test
    void construct_whenAFareIsGiven_carriesEachFact() {
        // When
        Journey rail = new Journey(STARTED, JourneyMode.RAIL_CONNECTED, Money.of("18000"),
                Optional.of(Money.of("15000")), Optional.empty());

        // Then
        assertThat(rail.mode()).isEqualTo(JourneyMode.RAIL_CONNECTED);
        assertThat(rail.fareAdmissible()).contains(Money.of("15000"));
    }

    @Test
    void construct_whenOnlyADistanceIsGiven_carriesIt() {
        // When — r.278(1)(iii)(B) reads a distance, not a fare
        Journey road = new Journey(STARTED, JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                Money.of("14000"), Optional.empty(), Optional.of(400));

        // Then
        assertThat(road.distanceKm()).contains(400);
    }

    @Test
    void construct_whenNeitherAFareNorADistanceIsGiven_isRefused() {
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.AIR, Money.of("20000"), Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fare");
    }

    @Test
    void construct_whenAnAmountIsNegativeOrTheDistanceIsNotPositive_isRefused() {
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.AIR, Money.of("-1"),
                        Optional.of(Money.of("100")), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.AIR, Money.of("100"),
                        Optional.of(Money.of("-1")), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                        Money.of("100"), Optional.empty(), Optional.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenAFactIsMissing_isRefused() {
        assertThatThrownBy(() -> new Journey(null, JourneyMode.AIR, Money.of("100"), Optional.of(Money.of("100")),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Journey(STARTED, null, Money.of("100"), Optional.of(Money.of("100")),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
