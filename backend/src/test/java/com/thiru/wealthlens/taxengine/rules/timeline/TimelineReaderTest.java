package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.json.RegimeJson;
import com.thiru.wealthlens.taxengine.rules.json.RuleJson;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The timeline (D-034): each rule, regime and node authored once, in a file named for it, under the
 * directory of its layer.
 *
 * <p>Every refusal starts from a copy of the real timeline changed in exactly one way, so the defect
 * <em>is</em> the change — the technique of {@code rulepacks-broken/}.
 */
class TimelineReaderTest {

    private static final Path REAL = Path.of("src/main/resources/rulepacks/timeline");
    private static final Path PACK_2026 = Path.of("src/main/resources/rulepacks/2026-27");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String SLAB_FILE = "law/rules/RATE-SLAB-NEW-TAX-REGIME.json";

    @TempDir
    Path copy;

    @Test
    void read_whenTheShippedTimelineIsRead_holdsExactlyThe2026PackLaw() {
        // Given — while both exist, the timeline and the 2026-27 pack must hold the same law (D-053)
        JsonNode rates = tree(PACK_2026.resolve("law/rates.json"));
        JsonNode regimes = tree(PACK_2026.resolve("law/regimes.json"));

        // When
        Timeline timeline = TimelineReader.read(REAL);

        // Then
        assertThat(timeline.rules()).isEqualTo(RuleJson.rules(rates, "law/rates.json"));
        assertThat(timeline.regimes()).isEqualTo(RegimeJson.regimes(regimes, "law/regimes.json"));
        assertThat(timeline.pipelineNodes()).hasSize(tree(PACK_2026.resolve("exec/pipelines.json"))
                .path("nodes").size())
                .containsKey(new NodeId("tax.netpayable"));
    }

    @Test
    void read_whenAFileIsMalformed_isRefusedNamingIt() throws IOException {
        // Given
        Path root = realCopy();
        Files.writeString(root.resolve(SLAB_FILE), "{ \"ruleId\": ");

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(SLAB_FILE);
    }

    @Test
    void read_whenARuleFileHoldsAMissingField_isRefusedWithItsPath() throws IOException {
        // Given
        Path root = realCopy();
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        rule.remove("axis");
        write(root.resolve(SLAB_FILE), rule);

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(SLAB_FILE)
                .hasMessageContaining("axis");
    }

    @Test
    void read_whenASecondFileClaimsARuleIdentity_isRefusedNamingIt() throws IOException {
        // Given — the copy keeps the identity inside, so one identity is now in two files
        Path root = realCopy();
        Files.copy(root.resolve(SLAB_FILE), root.resolve("law/rules/RATE-SLAB-COPY.json"));

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("law/rules/RATE-SLAB-COPY.json")
                .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME");
    }

    @Test
    void read_whenARegimeFileIsNamedForAnotherCode_isRefused() throws IOException {
        // Given
        Path root = realCopy();
        Files.move(
                root.resolve("law/regimes/NEW_TAX_REGIME.json"), root.resolve("law/regimes/OTHER_REGIME.json"));

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("law/regimes/OTHER_REGIME.json")
                .hasMessageContaining("NEW_TAX_REGIME");
    }

    @Test
    void read_whenANodeFileIsNamedForAnotherNode_isRefused() throws IOException {
        // Given
        Path root = realCopy();
        Files.move(root.resolve("exec/pipelines/tax.cess.json"), root.resolve("exec/pipelines/tax.other.json"));

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("exec/pipelines/tax.other.json")
                .hasMessageContaining("tax.cess");
    }

    @Test
    void read_whenTwoVersionsOfARuleOverlap_isRefusedNamingTheRule() throws IOException {
        // Given — the same version twice: both claim every day from 2026-04-01
        Path root = realCopy();
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        ArrayNode versions = (ArrayNode) rule.path("versions");
        versions.add(versions.get(0).deepCopy());
        write(root.resolve(SLAB_FILE), rule);

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(SLAB_FILE)
                .hasMessageContaining("overlap");
    }

    @Test
    void read_whenVersionsLeaveAGap_isAccepted() throws IOException {
        // Given — in force 2024-25, lapsed for 2025-26, back from 2026-27: a gap, not an overlap
        Path root = realCopy();
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        ArrayNode versions = (ArrayNode) rule.path("versions");
        ObjectNode earlier = (ObjectNode) versions.get(0).deepCopy();
        ((ObjectNode) earlier.path("validity")).put("from", "2024-04-01").put("to", "2025-03-31");
        versions.insert(0, earlier);
        write(root.resolve(SLAB_FILE), rule);

        // When
        Timeline timeline = TimelineReader.read(root);

        // Then
        assertThat(timeline.rules().get(new RuleId("RATE-SLAB-NEW-TAX-REGIME")).versions()).hasSize(2);
    }

    @Test
    void read_whenAFileLiesOutsideTheKnownDirectories_isRefusedNamingIt() throws IOException {
        // Given — forms/ has no reader yet (D-053), so a form file there cannot be silently skipped
        Path root = realCopy();
        Files.createDirectories(root.resolve("forms/itr"));
        Files.writeString(root.resolve("forms/itr/ITR-1.json"), "{}");

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("forms/itr/ITR-1.json")
                .hasMessageContaining("not a timeline file");
    }

    @Test
    void read_whenAKnownDirectoryHoldsANonJsonFile_isRefusedNamingIt() throws IOException {
        // Given
        Path root = realCopy();
        Files.writeString(root.resolve("law/rules/README.md"), "notes");

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("law/rules/README.md")
                .hasMessageContaining("not a timeline file");
    }

    private Path realCopy() throws IOException {
        try (Stream<Path> files = Files.walk(REAL)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path target = copy.resolve(REAL.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        }
        return copy;
    }

    private static JsonNode tree(Path file) {
        try {
            return MAPPER.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static void write(Path file, JsonNode node) throws IOException {
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }
}
