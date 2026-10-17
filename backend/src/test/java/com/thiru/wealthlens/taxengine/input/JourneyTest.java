package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A journey on leave, as entered for LTA (S4 r.278(1); Rules 1962 r.2B(1); D-100). Figures are fixtures. */
class JourneyTest {

    private static final LocalDate STARTED = LocalDate.of(2026, 5, 10);

    @Test
    void construct_whenAFareIsGiven_carriesEachFact() {
        // When
        Journey rail = new Journey(STARTED, JourneyMode.RAIL_CONNECTED, Money.of("18000"),
                Optional.of(Money.of("15000")), Optional.empty(), List.of());

        // Then
        assertThat(rail.mode()).isEqualTo(JourneyMode.RAIL_CONNECTED);
        assertThat(rail.fareAdmissible()).contains(Money.of("15000"));
    }

    @Test
    void construct_whenOnlyADistanceIsGiven_carriesIt() {
        // When — r.278(1)(iii)(B) reads a distance, not a fare
        Journey road = new Journey(STARTED, JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                Money.of("14000"), Optional.empty(), Optional.of(400), List.of());

        // Then
        assertThat(road.distanceKm()).contains(400);
    }

    @Test
    void construct_whenNeitherAFareNorADistanceIsGiven_isRefused() {
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.AIR, Money.of("20000"), Optional.empty(),
                        Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fare");
    }

    @Test
    void construct_whenAnAmountIsNegativeOrTheDistanceIsNotPositive_isRefused() {
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.AIR, Money.of("-1"),
                        Optional.of(Money.of("100")), Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.AIR, Money.of("100"),
                        Optional.of(Money.of("-1")), Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                        Money.of("100"), Optional.empty(), Optional.of(0), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenAFactIsMissing_isRefused() {
        assertThatThrownBy(() -> new Journey(null, JourneyMode.AIR, Money.of("100"), Optional.of(Money.of("100")),
                        Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Journey(STARTED, null, Money.of("100"), Optional.of(Money.of("100")),
                        Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenTheTravellersDoNotAddUpToTheJourney_isRefused() {
        // Given — 10,000 + 6,000 = 16,000 spent between them, not 18,000
        List<JourneyTraveller> travellers = List.of(
                new JourneyTraveller(Optional.empty(), Money.of("10000"), Optional.of(Money.of("8000"))),
                new JourneyTraveller(Optional.of(0), Money.of("6000"), Optional.of(Money.of("5000"))));

        // When / Then
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.RAIL_CONNECTED, Money.of("18000"),
                        Optional.of(Money.of("13000")), Optional.empty(), travellers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spent 16000.00 between them, not the journey's 18000.00");
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.RAIL_CONNECTED, Money.of("16000"),
                        Optional.of(Money.of("15000")), Optional.empty(), travellers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to 13000.00, not the journey's 15000.00");
    }

    @Test
    void construct_whenATravellerStatesAFareTheJourneyDoesNot_isRefused() {
        // Given — a per-kilometre journey has no fare, so its travellers state none
        List<JourneyTraveller> travellers = List.of(
                new JourneyTraveller(Optional.empty(), Money.of("14000"), Optional.of(Money.of("12000"))));

        // When / Then
        assertThatThrownBy(() -> new Journey(STARTED, JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT,
                        Money.of("14000"), Optional.empty(), Optional.of(400), travellers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("states a fare exactly where the journey does");
    }

    @Test
    void construct_whenATravellerHasANegativePosition_isRefused() {
        assertThatThrownBy(() -> new JourneyTraveller(Optional.of(-1), Money.of("100"), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("none or more, but was -1");
    }
}
