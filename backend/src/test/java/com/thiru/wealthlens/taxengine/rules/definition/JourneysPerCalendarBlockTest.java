package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Calendar blocks of the leave travel concession (S4 r.278(2); r.2B(2); D-100). Years counted by hand. */
class JourneysPerCalendarBlockTest {

    private static final JourneysPerCalendarBlock FROM_2022 = new JourneysPerCalendarBlock(2, 4, 2022);
    private static final JourneysPerCalendarBlock FROM_1986 = new JourneysPerCalendarBlock(2, 4, 1986);

    @Test
    void blockStarting_whenCountedFrom2022_placesEachYearInItsBlock() {
        // 2022–2025, then 2026–2029
        assertThat(FROM_2022.blockStarting(2025)).isEqualTo(2022);
        assertThat(FROM_2022.blockStarting(2026)).isEqualTo(2026);
        assertThat(FROM_2022.blockStarting(2029)).isEqualTo(2026);
    }

    @Test
    void blockStarting_whenCountedFrom1986_agreesWith2022() {
        // 1986 + 9 × 4 = 2022: r.2B and r.278 put the boundaries in the same place
        assertThat(FROM_1986.blockStarting(2025)).isEqualTo(2022);
        assertThat(FROM_1986.blockStarting(2026)).isEqualTo(2026);
    }

    @Test
    void blockStarting_whenBeforeTheCommencement_stillAligns() {
        // 2021 is in 2018–2021
        assertThat(FROM_2022.blockStarting(2021)).isEqualTo(2018);
    }

    @Test
    void construct_whenNoJourneyOrNoYear_isRefused() {
        assertThatThrownBy(() -> new JourneysPerCalendarBlock(0, 4, 2022)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JourneysPerCalendarBlock(2, 0, 2022)).isInstanceOf(IllegalArgumentException.class);
    }
}
