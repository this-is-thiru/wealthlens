package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.testsupport.StatusBoard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No plan of a finished item keeps an open step (W-200, D-398).
 *
 * <p>The bookkeeping audit of 2026-10-28 (G5) found 15 steps left as {@code - [ ]} in eight plans whose items were
 * done — every one done in its item's commit, never ticked, so the plan said the work was open while git said it was
 * finished. An item is finished when the status board says {@code D} or {@code —}; a step that was deliberately not
 * done says {@code not taken} with its reason, as W-180's do. A plan whose item has no board entry (a split parent such
 * as W-071) has no status to check, and is left alone.
 */
class FinishedPlanTest {

    private static final Path PLANS = Path.of("..", "docs", "taxplanning-build", "plans");
    private static final Path STATE = Path.of("..", "docs", "taxplanning-build", "STATE.md");
    private static final Pattern OPEN_STEP = Pattern.compile("^\\s*- \\[ \\] ");
    private static final String NOT_TAKEN = "not taken";

    @Test
    void openSteps_whenEveryPlanIsReadAgainstTheBoard_findsNoneOnAFinishedItem() throws IOException {
        // Given
        Map<String, String> board = StatusBoard.read(Files.readAllLines(STATE));
        Map<String, List<String>> plans = plans();

        // When
        List<String> open = openSteps(plans, board);

        // Then
        assertThat(board).as("the status board in STATE.md").isNotEmpty();
        assertThat(plans).as("the plans in docs/taxplanning-build/plans").isNotEmpty();
        assertThat(open).as("open steps in plans of finished items — tick each with the commit that did it, "
                + "or mark it \"not taken\" with why").isEmpty();
    }

    @Test
    void openSteps_whenADoneItemsPlanHasAnOpenStep_reportsIt() {
        // Given
        Map<String, List<String>> plans = Map.of("W-188", List.of("- [x] 1. Tick", "- [ ] 2. Audit"));

        // When
        List<String> open = openSteps(plans, Map.of("W-188", "D"));

        // Then
        assertThat(open).containsExactly("W-188: - [ ] 2. Audit");
    }

    @Test
    void openSteps_whenTheOpenStepSaysNotTaken_reportsNothing() {
        // Given
        Map<String, List<String>> plans = Map.of("W-180", List.of("- [ ] 1. Bump the pin — **not taken: closed, D-387**"));

        // When
        List<String> open = openSteps(plans, Map.of("W-180", "D"));

        // Then
        assertThat(open).isEmpty();
    }

    @Test
    void openSteps_whenTheItemIsStillOpenOrRetired_checksOnlyTheRetired() {
        // Given
        Map<String, List<String>> plans = Map.of(
                "W-189", List.of("- [ ] 1. Main"),
                "W-160", List.of("  - [ ] 2. Split"));

        // When
        List<String> open = openSteps(plans, Map.of("W-189", "·", "W-160", "—"));

        // Then
        assertThat(open).containsExactly("W-160:   - [ ] 2. Split");
    }

    @Test
    void openSteps_whenThePlanIsNotOnTheBoard_reportsNothing() {
        // Given
        Map<String, List<String>> plans = Map.of("W-071", List.of("- [ ] 1. Split"));

        // When
        List<String> open = openSteps(plans, Map.of("W-071a", "D"));

        // Then
        assertThat(open).isEmpty();
    }

    /** Each open step, not marked not taken, in a plan whose item the board calls finished — as "item: line". */
    private static List<String> openSteps(Map<String, List<String>> plans, Map<String, String> board) {
        List<String> open = new ArrayList<>();
        plans.forEach((item, lines) -> {
            String status = board.get(item);
            if (status != null && StatusBoard.isFinished(status)) {
                lines.stream()
                        .filter(line -> OPEN_STEP.matcher(line).find() && !line.contains(NOT_TAKEN))
                        .forEach(line -> open.add(item + ": " + line));
            }
        });
        return open.stream().sorted().toList();
    }

    /** Every {@code plans/W-*.md}, keyed by its item id (the file name without {@code .md}). */
    private static Map<String, List<String>> plans() throws IOException {
        try (Stream<Path> files = Files.list(PLANS)) {
            List<Path> plans = files.filter(file -> file.getFileName().toString().matches("W-.*\\.md")).toList();
            Map<String, List<String>> byItem = new HashMap<>();
            for (Path plan : plans) {
                byItem.put(plan.getFileName().toString().replaceFirst("\\.md$", ""), Files.readAllLines(plan));
            }
            return byItem;
        }
    }
}
