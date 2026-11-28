package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /**
     * The baseline's size: 99 when recorded on 2026-09-23, 98 since W-078k (D-192), 96 since W-088u8a and W-088u8b
     * each fixed one (set in W-088v, D-288), 0 after burning down all legacy violations. May only be lowered.
     */
    private static final int MAXIMUM_BASELINE_ENTRIES = 0;

    private static final Path SUPPRESSIONS = Path.of("..", "checkstyle-suppressions.xml");
    private static final Pattern FILES_ATTRIBUTE = Pattern.compile("files=\"([^\"]+)\"");

    @Test
    void baseline_whenCounted_neverExceedsTheRecordedMaximum() throws IOException {
        // Given
        List<String> lines = Files.readAllLines(SUPPRESSIONS);

        // When
        long entries = baselineLines(lines).size();

        // Then
        assertThat(entries)
                .as("checkstyle-suppressions.xml baseline entries — fix the violation instead of"
                        + " suppressing it; the baseline may only shrink")
                .isLessThanOrEqualTo(MAXIMUM_BASELINE_ENTRIES);
    }

    @Test
    void baseline_whenTargetsAreChecked_everySuppressedFileExists() throws IOException {
        // Given
        List<String> lines = Files.readAllLines(SUPPRESSIONS);

        // When
        List<String> missing = new ArrayList<>();
        for (String line : baselineLines(lines)) {
            Matcher matcher = FILES_ATTRIBUTE.matcher(line);
            if (!matcher.find()) {
                missing.add(line.trim());
                continue;
            }
            String relativePath = matcher.group(1)
                    .replace("[\\\\/]", "/")
                    .replace("\\.", ".")
                    .replaceAll("\\$$", "")
                    .replaceAll("^/", "");
            if (!Files.isRegularFile(Path.of(relativePath))) {
                missing.add(relativePath);
            }
        }

        // Then
        assertThat(missing)
                .as("checkstyle-suppressions.xml baseline entries must point to existing files —"
                        + " remove stale entries and lower MAXIMUM_BASELINE_ENTRIES")
                .isEmpty();
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

    private static List<String> baselineLines(List<String> lines) {
        boolean inside = false;
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line.contains("BASELINE START")) {
                inside = true;
            } else if (line.contains("BASELINE END")) {
                inside = false;
            } else if (inside && line.contains("<suppress ")) {
                result.add(line);
            }
        }
        return result;
    }
}
