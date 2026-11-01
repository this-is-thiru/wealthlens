package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.testsupport.StatusBoard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * No open Phase 9 row is lost in the switch to Phase 11 (W-229, D-435): every unfinished row of that phase, and every row the
 * board marks parked, must stand in {@code PARKED-ROWS.md} with a gate saying when it comes back.
 */
class ParkedRowsTest {

    private static final Path BUILD = Path.of("..", "docs", "taxplanning-build");

    private static final Pattern BOARD_ENTRY = Pattern.compile("(W-[0-9]{3}[a-z0-9]*) (\\S)");

    private static final Pattern REGISTER_ROW = Pattern.compile("^\\| (W-[0-9]{3}[a-z0-9]*) \\| ([^|]*) \\| ([^|]*) \\| ([^|]*) \\|");

    private static List<String> state() throws IOException {
        return Files.readAllLines(BUILD.resolve("STATE.md"));
    }

    /** Each register row's id and its gate. */
    private static Map<String, String> register() throws IOException {
        return Files.readAllLines(BUILD.resolve("PARKED-ROWS.md")).stream()
                .map(REGISTER_ROW::matcher)
                .filter(Matcher::find)
                .collect(Collectors.toMap(row -> row.group(1), row -> row.group(4).strip()));
    }

    @Test
    void register_whenAPhaseNineRowIsUnfinished_listsIt() throws IOException {
        // Given
        String phaseNine = state().stream().filter(line -> line.startsWith("Phase 9 ")).findFirst().orElseThrow();
        Matcher entry = BOARD_ENTRY.matcher(phaseNine.split("←")[0]);

        // When
        List<String> unfinished = entry.results()
                .filter(found -> !StatusBoard.isFinished(found.group(2)))
                .map(found -> found.group(1))
                .toList();

        // Then
        assertThat(unfinished).as("the unfinished Phase 9 rows").isNotEmpty();
        assertThat(register().keySet()).containsAll(unfinished);
    }

    @Test
    void register_whenTheBoardParksARow_listsIt() throws IOException {
        // When
        List<String> parked = StatusBoard.read(state()).entrySet().stream()
                .filter(row -> row.getValue().equals("K"))
                .map(Map.Entry::getKey)
                .toList();

        // Then
        assertThat(parked).as("the parked rows").isNotEmpty();
        assertThat(register().keySet()).containsAll(parked);
    }

    @Test
    void register_whenRead_givesEveryRowAGate() throws IOException {
        // When / Then
        assertThat(register()).isNotEmpty().allSatisfy((row, gate) -> assertThat(gate).as(row).isNotBlank());
    }
}
