package com.thiru.wealthlens.taxengine.rules.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import com.thiru.wealthlens.testsupport.StatusBoard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every {@code PLANNED} section names an item still to be built (D-159, D-161). A pointer at an item that is not on
 * the status board, or is done or retired, leaves a section claimed by nothing — W-078c's {@code W-078d}, never in the
 * backlog, and nine sections left behind by W-046, W-049, W-060 and W-067 went unnoticed that way. The status board in
 * {@code STATE.md} is read because it is the one place an item's status is kept. A section in the first scope with no item
 * at all ({@code DEFERRED BUILT_FIRST}) is refused too: 42 sat there unnamed from W-045o until W-078e2 (D-162).
 */
class CoverageBacklogTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final Path STATE = Path.of("..", "docs", "taxplanning-build", "STATE.md");

    @Test
    void stalePointers_whenTheShippedRegisterIsReadAgainstTheBoard_findsNone() throws IOException {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);
        Map<String, String> board = StatusBoard.read(Files.readAllLines(STATE));

        // When
        List<String> stale = stalePointers(CoverageRegister.of(timeline).sections(), board);

        // Then
        assertThat(board).as("the status board in STATE.md").isNotEmpty();
        assertThat(stale).as("PLANNED sections whose item is missing, done or retired — re-point or reclassify them")
                .isEmpty();
    }

    @Test
    void unitemisedGaps_whenTheShippedRegisterIsRead_findsNone() {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);

        // When
        List<SectionRef> gaps = unitemisedGaps(CoverageRegister.of(timeline).sections());

        // Then
        assertThat(gaps).as("DEFERRED BUILT_FIRST sections — in the first scope with no item; give each an item (D-162)")
                .isEmpty();
    }

    @Test
    void unitemisedGaps_whenASectionIsInTheFirstScopeWithNoItem_reportsIt() {
        // Given
        SectionRef gap = new SectionRef(SectionRef.Act.INCOME_TAX_ACT_2025, "392");
        Map<SectionRef, Coverage> sections = Map.of(
                gap, new Coverage.Deferred(Coverage.ScopeTier.BUILT_FIRST, Set.of(PersonType.INDIVIDUAL)),
                new SectionRef(SectionRef.Act.INCOME_TAX_ACT_2025, "28"),
                new Coverage.Deferred(Coverage.ScopeTier.BUILT_LATER, Set.of()));

        // When
        List<SectionRef> gaps = unitemisedGaps(sections);

        // Then
        assertThat(gaps).containsExactly(gap);
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
        return StatusBoard.isFinished(status) ? Optional.of(section + " names " + item + ", which is done or retired")
                : Optional.empty();
    }

    /**
     * Sections {@code DEFERRED BUILT_FIRST}: in the first scope with no item — the tier exists only to be itemised, and
     * W-078e2 itemised all 42 that sat in it since W-045o (D-162).
     */
    private static List<SectionRef> unitemisedGaps(Map<SectionRef, Coverage> sections) {
        return sections.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Coverage.Deferred deferred
                        && deferred.tier() == Coverage.ScopeTier.BUILT_FIRST)
                .map(Map.Entry::getKey)
                .toList();
    }
}
