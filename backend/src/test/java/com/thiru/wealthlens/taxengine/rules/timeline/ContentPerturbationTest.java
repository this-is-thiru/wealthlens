package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Invariant I27 / test T23-1 (property P3): the timeline's content — the section catalogue, the Acts' section lists
 * and the coverage register — changes no number. Every text in it is rewritten, or the whole of {@code content/}
 * removed, and each shipped year compiles to the same pack, file for file, so the engine reads exactly what it read
 * before (W-146). The shipped timeline holds no {@code forms/} yet, so that layer compiles empty in every shipped
 * year; W-090c brings the first forms and extends this perturbation to them (D-521).
 */
class ContentPerturbationTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** The fields a content file is named by, which the reader checks against the file name; all else is text. */
    private static final Set<String> IDENTITY = Set.of("key", "act");

    @TempDir
    Path copy;

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void compile_whenEveryContentTextIsRewritten_compilesTheSamePack(int year) throws IOException {
        // Given
        TaxYearId taxYear = TaxYearId.of(year);
        CompiledPack shipped = RulePackCompiler.compile(TimelineReader.read(TIMELINE), taxYear);
        Path root = timelineCopy();
        int rewritten = rewriteContent(root);

        // When
        CompiledPack perturbed = RulePackCompiler.compile(TimelineReader.read(root), taxYear);

        // Then — the rewrite reached every content file, and no compiled file or rule checksum moved
        assertThat(rewritten).isGreaterThan(60);
        assertThat(perturbed.files()).isEqualTo(shipped.files());
        assertThat(perturbed.ruleChecksums()).isEqualTo(shipped.ruleChecksums());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void compile_whenTheContentDirectoryIsRemoved_compilesTheSamePack(int year) throws IOException {
        // Given
        TaxYearId taxYear = TaxYearId.of(year);
        CompiledPack shipped = RulePackCompiler.compile(TimelineReader.read(TIMELINE), taxYear);
        Path root = timelineCopy();
        try (Stream<Path> content = Files.walk(root.resolve("content"))) {
            for (Path file : content.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }

        // When
        CompiledPack withoutContent = RulePackCompiler.compile(TimelineReader.read(root), taxYear);

        // Then
        assertThat(withoutContent.files()).isEqualTo(shipped.files());
        assertThat(withoutContent.ruleChecksums()).isEqualTo(shipped.ruleChecksums());
    }

    /** Appends to every string under {@code content/} except the identity fields; returns the files rewritten. */
    private static int rewriteContent(Path root) throws IOException {
        int count = 0;
        try (Stream<Path> files = Files.walk(root.resolve("content"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                JsonNode node = MAPPER.readTree(Files.readString(file));
                rewrite(node);
                Files.writeString(file, MAPPER.writeValueAsString(node));
                count++;
            }
        }
        return count;
    }

    private static void rewrite(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (String name : Set.copyOf(object.propertyNames())) {
                JsonNode value = object.get(name);
                if (value.isString() && !IDENTITY.contains(name)) {
                    object.put(name, value.asString() + " (rewritten)");
                } else {
                    rewrite(value);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode value = array.get(i);
                if (value.isString()) {
                    array.set(i, value.asString() + " (rewritten)");
                } else {
                    rewrite(value);
                }
            }
        }
    }

    private Path timelineCopy() throws IOException {
        try (Stream<Path> files = Files.walk(TIMELINE)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path target = copy.resolve(TIMELINE.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        }
        return copy;
    }
}
