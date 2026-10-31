package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.PackDiff.Finding;
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
 * The publish diff (D-037, D-057): what a rule change changes, listed so a reviewer signs off a list.
 *
 * <p>Every case compiles the shipped timeline twice, one side edited in a copy, so each finding
 * is the edit and nothing else.
 */
class PackDiffTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final TaxYearId TY_2026 = new TaxYearId(2026);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final RuleId SLAB = new RuleId("RATE-SLAB-NEW-TAX-REGIME");
    private static final RuleId FIRST_SCHEDULE = new RuleId("RATE-SLAB-FIRST-SCHEDULE");
    private static final String SLAB_FILE = "law/rules/RATE-SLAB-NEW-TAX-REGIME.json";

    @TempDir
    Path copy;

    /** The rule's last version — the one in force in 2026-27, since TY 2025-26 has its own before it. */
    private static JsonNode inForceIn2026(JsonNode rule) {
        JsonNode versions = rule.path("versions");
        return versions.get(versions.size() - 1);
    }

    @Test
    void between_whenThePacksAreIdentical_findsNothing() {
        // When
        PackDiff diff = PackDiff.between(compile(TIMELINE, TY_2026), compile(TIMELINE, TY_2026));

        // Then
        assertThat(diff.findings()).isEmpty();
    }

    @Test
    void between_whenOneBandRateChanges_findsExactlyThatValue() throws IOException {
        // Given
        Path edited = editedCopy(rule -> ((ObjectNode) inForceIn2026(rule).path("cap").path("bands").get(1))
                .put("rate", "6"));

        // When
        PackDiff diff = PackDiff.between(compile(TIMELINE, TY_2026), compile(edited, TY_2026));

        // Then
        assertThat(diff.findings())
                .containsExactly(new Finding.ValueChanged(SLAB, "2026-04-01", "cap.bands[1].rate", "5", "6"));
    }

    @Test
    void between_whenARuleIsRemoved_findsItRemovedAndTheReverseFindsItAdded() throws IOException {
        // Given
        Path root = timelineCopy();
        Files.delete(root.resolve("law/rules/RATE-SLAB-FIRST-SCHEDULE.json"));
        CompiledPack full = compile(TIMELINE, TY_2026);
        CompiledPack without = compile(root, TY_2026);

        // When / Then
        assertThat(PackDiff.between(full, without).findings()).containsExactly(new Finding.RuleRemoved(FIRST_SCHEDULE));
        assertThat(PackDiff.between(without, full).findings()).containsExactly(new Finding.RuleAdded(FIRST_SCHEDULE));
    }

    @Test
    void between_whenAVersionIsAdded_findsTheVersionNotItsValues() throws IOException {
        // Given — a pending-notification version, which every year's pack holds
        Path edited = editedCopy(rule -> {
            ObjectNode pending = (ObjectNode) rule.path("versions").get(0).deepCopy();
            pending.set("validity", MAPPER.createObjectNode()
                    .put("type", "PendingNotification")
                    .put("interimRule", "RATE-SLAB-FIRST-SCHEDULE")
                    .put("interimDescription", "the first-schedule rates apply meanwhile"));
            pending.put("legalStatus", "PENDING_NOTIFICATION");
            ((ArrayNode) rule.path("versions")).add(pending);
        });

        // When
        PackDiff diff = PackDiff.between(compile(TIMELINE, TY_2026), compile(edited, TY_2026));

        // Then
        assertThat(diff.findings()).containsExactly(new Finding.VersionAdded(SLAB, "pending"));
    }

    @Test
    void between_whenAVersionIsClosed_findsItsEndDateNotARemovalAndAnAddition() throws IOException {
        // Given
        Path edited = editedCopy(rule -> ((ObjectNode) inForceIn2026(rule).path("validity"))
                .put("to", "2027-03-31"));

        // When
        PackDiff diff = PackDiff.between(compile(TIMELINE, TY_2026), compile(edited, TY_2026));

        // Then
        assertThat(diff.findings())
                .containsExactly(new Finding.ValueChanged(SLAB, "2026-04-01", "validity.to", "(absent)", "2027-03-31"));
    }

    @Test
    void between_whenOnlyANoteChanges_findsNothing() throws IOException {
        // Given
        Path edited = editedCopy(rule -> ((ObjectNode) rule).put("_note", "an edited note"));

        // When
        PackDiff diff = PackDiff.between(compile(TIMELINE, TY_2026), compile(edited, TY_2026));

        // Then
        assertThat(diff.findings()).isEmpty();
    }

    @Test
    void between_whenTwoYearsHoldTheSameLaw_findsNothingAndNamesBothYears() {
        // Given — the cross-year use W-139 builds on
        TaxYearId next = new TaxYearId(2027);

        // When
        PackDiff diff = PackDiff.between(compile(TIMELINE, TY_2026), compile(TIMELINE, next));

        // Then
        assertThat(diff.from()).isEqualTo(TY_2026);
        assertThat(diff.to()).isEqualTo(next);
        assertThat(diff.findings()).isEmpty();
    }

    private static CompiledPack compile(Path root, TaxYearId year) {
        return RulePackCompiler.compile(TimelineReader.read(root), year);
    }

    private Path editedCopy(java.util.function.Consumer<JsonNode> edit) throws IOException {
        Path root = timelineCopy();
        JsonNode rule = MAPPER.readTree(Files.readString(root.resolve(SLAB_FILE)));
        edit.accept(rule);
        Files.writeString(root.resolve(SLAB_FILE), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rule));
        return root;
    }

    private Path timelineCopy() {
        try (Stream<Path> files = Files.walk(TIMELINE)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path target = copy.resolve(TIMELINE.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy the timeline", e);
        }
        return copy;
    }
}
