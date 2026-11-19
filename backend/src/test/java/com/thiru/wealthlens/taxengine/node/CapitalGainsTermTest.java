package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The first day a sale is long-term (W-262): the day after the same date that many months on, by D-303's ruling that a
 * lot is long-term only when held for more than the period. Dates worked on a calendar, not by the method.
 */
class CapitalGainsTermTest {

    @Test
    void longTermFrom_whenTwelveMonths_isTheDayAfterTheSameDateAYearOn() {
        assertThat(CapitalGainsTerm.longTermFrom(LocalDate.of(2025, 6, 1), 12)).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    void longTermFrom_whenTheMonthOnIsShorter_countsFromItsLastDay() {
        // 31 August + 6 months has no 31 February: the period ends on 28 February 2026, so long-term from 1 March
        assertThat(CapitalGainsTerm.longTermFrom(LocalDate.of(2025, 8, 31), 6)).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void longTermFrom_whenTwentyFourMonthsFromALeapDay_isTheFirstOfMarchTwoYearsOn() {
        assertThat(CapitalGainsTerm.longTermFrom(LocalDate.of(2024, 2, 29), 24)).isEqualTo(LocalDate.of(2026, 3, 1));
    }
}
