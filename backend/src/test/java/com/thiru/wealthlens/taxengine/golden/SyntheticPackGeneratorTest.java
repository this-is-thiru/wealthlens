package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Running {@code tools/w146_synthetic_pack.py} again keeps every row kind of the committed synthetic pack (W-259, C3a, C4). In a
 * copy of the repo's two timelines, the generator must leave every committed file in place, and every file it writes from its own
 * synthetic law must come out as committed. A row it copies from the shipped TY 2026-27 timeline may differ after a shipped data
 * change; those are listed, never failed, so editing a shipped row does not break the build.
 */
class SyntheticPackGeneratorTest {

    /** The marker {@code copied()} writes into every row it copies; pipelines are copied as they are, without one. */
    private static final String COPIED = "Copied from the shipped TY 2026-27 timeline";
    private static final String PIPELINES = "timeline/exec/pipelines/";

    private static Set<String> files(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                Path target = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
    }

    private static boolean isCopied(String relative, Path file) throws IOException {
        return relative.startsWith(PIPELINES) || Files.readString(file, StandardCharsets.UTF_8).contains(COPIED);
    }

    @Test
    void generator_whenRunAgain_keepsEveryCommittedFileAndWritesItsOwnLawAsCommitted(@TempDir Path repo) throws Exception {
        // Given — a copy of the shipped timeline and the committed synthetic pack, laid out as the generator expects
        Path backend = Path.of("").toAbsolutePath();
        Path committed = backend.resolve("src/test/resources/rulepacks-synthetic");
        copyTree(backend.resolve("src/main/resources/rulepacks/timeline"), repo.resolve("backend/src/main/resources/rulepacks/timeline"));
        Path regenerated = repo.resolve("backend/src/test/resources/rulepacks-synthetic");
        copyTree(committed, regenerated);

        // When
        Process generator;
        try {
            generator = new ProcessBuilder("python3", "-I", backend.getParent().resolve("docs/taxplanning-build/tools/w146_synthetic_pack.py")
                    .toString(), repo.toString()).redirectErrorStream(true).start();
        } catch (IOException e) {
            fail("python3 is needed to run the synthetic pack's generator (W-259 C4): " + e.getMessage());
            return;
        }
        String output = new String(generator.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(generator.waitFor(120, TimeUnit.SECONDS)).as("the generator finished").isTrue();
        assertThat(generator.exitValue()).as(output).isZero();

        // Then — nothing committed is lost, and nothing of the generator's own law differs
        Set<String> before = files(committed);
        Set<String> after = files(regenerated);
        Set<String> lost = new TreeSet<>(before);
        lost.removeAll(after);
        List<String> ownDiffers = new ArrayList<>();
        List<String> copiesDiffer = new ArrayList<>();
        for (String relative : after) {
            Path made = regenerated.resolve(relative);
            boolean same = before.contains(relative) && Files.mismatch(made, committed.resolve(relative)) == -1;
            if (!same) {
                (isCopied(relative, made) ? copiesDiffer : ownDiffers).add(relative);
            }
        }
        assertThat(lost).as("committed files a regeneration deletes").isEmpty();
        assertThat(ownDiffers).as("files of the generator's own synthetic law that differ; copies that differ: " + copiesDiffer).isEmpty();
    }
}
