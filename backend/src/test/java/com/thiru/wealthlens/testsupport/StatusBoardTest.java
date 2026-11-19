package com.thiru.wealthlens.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusBoardTest {

    @Test
    void read_whenTheBoardBlockIsRead_mapsEachItemToItsStatus() {
        // Given
        List<String> lines = List.of("intro W-001 D", "## Status board", "```",
                "Phase 8    W-078c D  W-078d ·  W-071e —", "```", "Counts — done", "W-500 D");

        // When
        Map<String, String> board = StatusBoard.read(lines);

        // Then
        assertThat(board).containsExactlyInAnyOrderEntriesOf(Map.of("W-078c", "D", "W-078d", "·", "W-071e", "—"));
    }

    @Test
    void isFinished_whenEachStatusIsAsked_answersOnlyForDoneAndRetired() {
        // Given / When / Then
        assertThat(StatusBoard.isFinished("D")).isTrue();
        assertThat(StatusBoard.isFinished("—")).isTrue();
        assertThat(StatusBoard.isFinished("·")).isFalse();
        assertThat(StatusBoard.isFinished("E")).isFalse();
        assertThat(StatusBoard.isFinished("R")).isFalse();
        assertThat(StatusBoard.isFinished("B")).isFalse();
    }
}
