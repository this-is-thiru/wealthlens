package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Keeps the Checkstyle legacy baseline shrinking, never growing (decision D-045, item W-045a).
 *
 * <p>{@code checkstyle-suppressions.xml} lets code that predates the strict rule set keep building.
 * Without this test, the easy way to silence a new violation would be one more line in that file,
 * and the baseline would quietly become the rule set. Fixing a legacy violation removes its line;
 * lower {@link #MAXIMUM_BASELINE_ENTRIES} to the new count in the same change so it cannot grow back.
 */
class CheckstyleBaselineTest {

    /** The baseline's size: 99 when recorded on 2026-09-23, 98 since W-078k (D-192). May only be lowered. */
    private static final int MAXIMUM_BASELINE_ENTRIES = 98;

    private static final Path SUPPRESSIONS = Path.of("..", "checkstyle-suppressions.xml");

    @Test
    void baseline_whenCounted_neverExceedsTheRecordedMaximum() throws IOException {
        // Given
        List<String> lines = Files.readAllLines(SUPPRESSIONS);

        // When
        long entries = baselineEntries(lines);

        // Then
        assertThat(entries)
                .as("checkstyle-suppressions.xml baseline entries — fix the violation instead of"
                        + " suppressing it; the baseline may only shrink")
                .isLessThanOrEqualTo(MAXIMUM_BASELINE_ENTRIES);
    }

    @Test
    void baseline_whenMarkersAreRead_isDelimitedExactlyOnce() throws IOException {
        // Given
        List<String> lines = Files.readAllLines(SUPPRESSIONS);

        // When
        long starts = lines.stream().filter(line -> line.contains("BASELINE START")).count();
        long ends = lines.stream().filter(line -> line.contains("BASELINE END")).count();

        // Then
        assertThat(starts).as("BASELINE START markers").isEqualTo(1);
        assertThat(ends).as("BASELINE END markers").isEqualTo(1);
    }

    private static long baselineEntries(List<String> lines) {
        boolean inside = false;
        long count = 0;
        for (String line : lines) {
            if (line.contains("BASELINE START")) {
                inside = true;
            } else if (line.contains("BASELINE END")) {
                inside = false;
            } else if (inside && line.contains("<suppress ")) {
                count++;
            }
        }
        return count;
    }
}
