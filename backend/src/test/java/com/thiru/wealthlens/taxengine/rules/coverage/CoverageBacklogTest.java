package com.thiru.wealthlens.taxengine.rules.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every {@code PLANNED} section names an item still to be built (D-159, D-161). A pointer at an item that is not on
 * the status board, or is done or retired, leaves a section claimed by nothing — W-078c's {@code W-078d}, never in the
 * backlog, and nine sections left behind by W-046, W-049, W-060 and W-067 went unnoticed that way. The status board in
 * {@code STATE.md} is read because it is the one place an item's status is kept.
 */
class CoverageBacklogTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final Path STATE = Path.of("..", "docs", "taxplanning-build", "STATE.md");
    private static final Pattern BOARD_ENTRY = Pattern.compile("(W-[0-9]{3}[a-z0-9]*) (\\S)");
    private static final Set<String> FINISHED = Set.of("D", "—");

    @Test
    void stalePointers_whenTheShippedRegisterIsReadAgainstTheBoard_findsNone() throws IOException {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);
        Map<String, String> board = statusBoard(Files.readAllLines(STATE));

        // When
        List<String> stale = stalePointers(CoverageRegister.of(timeline).sections(), board);

        // Then
        assertThat(board).as("the status board in STATE.md").isNotEmpty();
        assertThat(stale).as("PLANNED sections whose item is missing, done or retired — re-point or reclassify them")
                .isEmpty();
    }

    @Test
    void stalePointers_whenASectionNamesADoneOrAbsentItem_reportsEach() {
        // Given
        Coverage.Planned atDone = new Coverage.Planned(Set.of(PersonType.INDIVIDUAL), "W-046");
        Coverage.Planned atAbsent = new Coverage.Planned(Set.of(PersonType.INDIVIDUAL), "W-999");
        Coverage.Planned atLive = new Coverage.Planned(Set.of(PersonType.INDIVIDUAL), "W-079");
        Map<SectionRef, Coverage> sections = Map.of(
                new SectionRef(SectionRef.Act.INCOME_TAX_ACT_2025, "4"), atDone,
                new SectionRef(SectionRef.Act.INCOME_TAX_ACT_2025, "5"), atAbsent,
                new SectionRef(SectionRef.Act.INCOME_TAX_ACT_2025, "13"), atLive);
        Map<String, String> board = Map.of("W-046", "D", "W-079", "·");

        // When
        List<String> stale = stalePointers(sections, board);

        // Then
        assertThat(stale).hasSize(2)
                .anySatisfy(line -> assertThat(line).contains("W-046").contains("done or retired"))
                .anySatisfy(line -> assertThat(line).contains("W-999").contains("not on the status board"));
    }

    @Test
    void statusBoard_whenTheBoardBlockIsRead_mapsEachItemToItsStatus() {
        // Given
        List<String> lines = List.of("intro W-001 D", "## Status board", "```",
                "Phase 8    W-078c D  W-078d ·  W-071e —", "```", "Counts — done", "W-500 D");

        // When
        Map<String, String> board = statusBoard(lines);

        // Then
        assertThat(board).containsExactlyInAnyOrderEntriesOf(Map.of("W-078c", "D", "W-078d", "·", "W-071e", "—"));
    }

    /** Each {@code PLANNED} section whose item is not on the board, or is done or retired, described. */
    private static List<String> stalePointers(Map<SectionRef, Coverage> sections, Map<String, String> board) {
        return sections.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Coverage.Planned)
                .flatMap(entry -> staleness(entry.getKey(), ((Coverage.Planned) entry.getValue()).backlogItem(), board)
                        .stream())
                .sorted()
                .toList();
    }

    private static Optional<String> staleness(SectionRef section, String item, Map<String, String> board) {
        String status = board.get(item);
        if (status == null) {
            return Optional.of(section + " names " + item + ", which is not on the status board");
        }
        return FINISHED.contains(status) ? Optional.of(section + " names " + item + ", which is done or retired")
                : Optional.empty();
    }

    /** The board: the lines from {@code ## Status board} up to the counts line, each {@code W-0nn X} entry. */
    private static Map<String, String> statusBoard(List<String> lines) {
        Map<String, String> board = new HashMap<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.startsWith("## Status board")) {
                inside = true;
            } else if (inside && line.startsWith("Counts")) {
                break;
            } else if (inside) {
                Matcher entry = BOARD_ENTRY.matcher(line);
                while (entry.find()) {
                    board.put(entry.group(1), entry.group(2));
                }
            }
        }
        return board;
    }
}
