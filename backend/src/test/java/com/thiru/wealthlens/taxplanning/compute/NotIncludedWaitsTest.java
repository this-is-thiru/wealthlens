package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.testsupport.StatusBoard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A draft read's fixed text that says what it does not include, and names the work item that will include it, must
 * name one not yet finished (W-330, D-719): once that item is built the sentence is false, and W-119b1 shipped with the
 * panel still saying its gap was open. A ruling cited as the reason for a choice — {@code (W-119c C3a)} — is not a
 * waiting item and is not checked.
 */
class NotIncludedWaitsTest {

    private static final Path BACKLOG = Path.of("..", "docs", "taxplanning-build", "BACKLOG.md");

    /** A bare item in brackets, the way a text names what it waits on: {@code (W-113a)}, never {@code (W-114a C4a)}. */
    private static final Pattern WAITS_ON = Pattern.compile("\\((W-[0-9]{3}[a-z0-9]*)\\)");

    @Test
    void notIncluded_whenATextWaitsOnAnItem_namesOneNotYetFinished() throws IOException {
        // Given
        Map<String, String> board = StatusBoard.read(Files.readAllLines(BACKLOG));
        List<String> texts = new ArrayList<>();
        ForbiddenWordingTest.READS.forEach(read -> ForbiddenWordingTest.notIncluded(read)
                .forEach(text -> texts.add(read.getSimpleName() + ": " + text)));
        DraftOnboardingService.NOT_MEASURED.values().forEach(text -> texts.add("DraftOnboardingService: " + text));
        DraftComputationService.BAND_NOT_INCLUDED.forEach(text -> texts.add("DraftComputationService: " + text));

        // When
        List<String> stale = texts.stream().filter(text -> waitsOnAFinishedItem(text, board)).toList();

        // Then
        assertThat(board).as("the open items in BACKLOG.md").isNotEmpty();
        assertThat(texts).as("the fixed texts").isNotEmpty();
        assertThat(stale).as("fixed texts waiting on a finished item (not in BACKLOG.md, or done there)").isEmpty();
    }

    @Test
    void waitsOnAFinishedItem_whenTheItemIsDoneOrOnlyCitedAsARuling_isFoundOnlyForTheBareDoneItem() {
        // Given
        Map<String, String> board = Map.of("W-100", "D", "W-101", "·");

        // When / Then
        assertThat(waitsOnAFinishedItem("Not evidenced yet (W-100)", board)).isTrue();
        assertThat(waitsOnAFinishedItem("Not measured yet (W-101)", board)).isFalse();
        assertThat(waitsOnAFinishedItem("Taken as equal each month (W-100 C3a)", board)).isFalse();
        assertThat(waitsOnAFinishedItem("Not shown yet (W-102)", board)).isTrue();
    }

    private static boolean waitsOnAFinishedItem(String text, Map<String, String> board) {
        Matcher item = WAITS_ON.matcher(text);
        while (item.find()) {
            if (StatusBoard.isFinished(board, item.group(1))) {
                return true;
            }
        }
        return false;
    }
}
