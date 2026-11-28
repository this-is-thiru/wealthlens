package com.thiru.wealthlens.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusBoardTest {

    @Test
    void read_whenBacklogTableRowsAreRead_mapsEachItemToItsStatus() {
        // Given
        List<String> lines = List.of(
                "# Backlog",
                "intro W-001 D",
                "| Id | Status | Item | Source | When |",
                "|---|---|---|---|---|",
                "| W-078c | D | Done item | src | when |",
                "| W-078d | · | Open item | src | when |",
                "| W-071e | — | Retired item | src | when |",
                "trailing prose mentioning W-500 D");

        // When
        Map<String, String> board = StatusBoard.read(lines);

        // Then
        assertThat(board).containsExactlyInAnyOrderEntriesOf(Map.of("W-078c", "D", "W-078d", "·", "W-071e", "—"));
    }

    @Test
    void isFinished_whenItemIsCheckedAgainstBoard_treatsAbsentDoneAndRetiredAsFinished() {
        // Given
        Map<String, String> board = Map.of("W-078c", "D", "W-078d", "·", "W-071e", "—", "W-238e2", "B");

        // When / Then
        assertThat(StatusBoard.isFinished(board, "W-001")).isTrue();
        assertThat(StatusBoard.isFinished(board, "W-078c")).isTrue();
        assertThat(StatusBoard.isFinished(board, "W-071e")).isTrue();
        assertThat(StatusBoard.isFinished(board, "W-078d")).isFalse();
        assertThat(StatusBoard.isFinished(board, "W-238e2")).isFalse();
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
