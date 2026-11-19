package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** O8's knob (W-238b C2a): a request may narrow the exhaustive check, never widen it past the setting. */
class OptimiserSettingsTest {

    private static final OptimiserSettings SIX = new OptimiserSettings(6);

    @Test
    void breadthFor_whenNoneIsAsked_isTheSetting() {
        // Given / When / Then
        assertThat(SIX.breadthFor(Optional.empty())).isEqualTo(6);
    }

    @Test
    void breadthFor_whenLessIsAsked_isWhatWasAsked() {
        // Given / When / Then
        assertThat(SIX.breadthFor(Optional.of(1))).isEqualTo(1);
    }

    @Test
    void breadthFor_whenTheSettingItselfIsAsked_isAllowed() {
        // Given / When / Then
        assertThat(SIX.breadthFor(Optional.of(6))).isEqualTo(6);
    }

    @Test
    void breadthFor_whenMoreIsAsked_isRefusedNamingTheSetting() {
        // Given / When / Then
        assertThatThrownBy(() -> SIX.breadthFor(Optional.of(7))).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most 6").hasMessageContaining("not 7");
    }

    @Test
    void constructor_whenTheSettingIsBelowOne_failsTheStart() {
        // Given / When / Then — an absent key binds to 0, so a missing yaml entry fails here too
        assertThatThrownBy(() -> new OptimiserSettings(0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exhaustive-breadth must be at least 1, not 0");
    }
}
