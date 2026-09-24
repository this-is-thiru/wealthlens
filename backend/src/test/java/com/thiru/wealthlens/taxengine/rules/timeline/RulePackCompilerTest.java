package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A year's rule pack compiled from the timeline (D-034, D-054): equal to the pack it replaces, pinned
 * by its lock, and holding only published versions that touch the year.
 */
class RulePackCompilerTest {

    private static final TaxYearId TY_2026 = new TaxYearId(2026);
    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final Path TODAY = Path.of("src/main/resources/rulepacks/2026-27");
    private static final Path LOCK = Path.of("src/main/resources/rulepacks/2026-27.lock.json");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String SLAB = "RATE-SLAB-NEW-TAX-REGIME";
    private static final String SLAB_FILE = "law/rules/" + SLAB + ".json";

    @TempDir
    Path copy;

    @Test
    void compile_whenThe2026TimelineIsCompiled_equalsTodaysPackLayerByLayer() {
        // When
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(TIMELINE), TY_2026);

        // Then — every layer file, canonically, with identities in id order (D-054 B1)
        for (RulePackLayer layer : RulePackLayer.values()) {
            for (String file : layer.files()) {
                assertThat(compiled.files().get(file))
                        .as(file)
                        .isEqualTo(RulePackCompiler.render(identitiesInOrder(tree(TODAY.resolve(file)))));
            }
        }
    }

    @Test
    void compile_whenAssembled_givesTheSameRulePackAsTodaysFiles() throws IOException {
        // Given — today's files assembled directly; the provider itself now compiles the timeline
        Map<String, String> todaysFiles = new HashMap<>();
        for (RulePackLayer layer : RulePackLayer.values()) {
            layer.files().forEach(file -> todaysFiles.put(file, text(TODAY.resolve(file))));
        }
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(TIMELINE), TY_2026);
        RulePack today = new JsonPackAssembler(instruments()).assemble(TY_2026, lock().layers(), todaysFiles);

        // When
        RulePack fromTimeline = new JsonPackAssembler(instruments()).assemble(TY_2026, lock().layers(), compiled.files());

        // Then
        assertThat(fromTimeline.rules()).isEqualTo(today.rules());
        assertThat(fromTimeline.regimes()).isEqualTo(today.regimes());
        assertThat(fromTimeline.components()).isEqualTo(today.components());
        assertThat(fromTimeline.disallowanceSets()).isEqualTo(today.disallowanceSets());
        assertThat(fromTimeline.conditionSets()).isEqualTo(today.conditionSets());
    }

    @Test
    void verify_whenTheTimelineMatchesTheLock_passes() throws IOException {
        // Given
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(TIMELINE), TY_2026);

        // When / Then
        assertThatCode(() -> lock().verify(compiled)).doesNotThrowAnyException();
    }

    @Test
    void verify_whenARuleChangedWithoutALockUpdate_isRefusedNamingTheRule() throws IOException {
        // Given — one band's rate changed in the timeline, the lock untouched
        Path root = timelineCopy();
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        ObjectNode band = (ObjectNode) rule.path("versions").get(0).path("cap").path("bands").get(1);
        band.put("rate", "6");
        write(root.resolve(SLAB_FILE), rule);
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(root), TY_2026);

        // When / Then
        assertThatThrownBy(() -> lock().verify(compiled))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(SLAB + " changed")
                .hasMessageNotContaining("RATE-SLAB-FIRST-SCHEDULE changed");
    }

    @Test
    void compile_whenARulesOnlyVersionIsADraft_leavesTheRuleOut() throws IOException {
        // Given
        Path root = timelineCopy();
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        ((ObjectNode) rule.path("versions").get(0)).put("publicationStatus", "DRAFT");
        write(root.resolve(SLAB_FILE), rule);

        // When
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(root), TY_2026);

        // Then
        assertThat(compiled.ruleChecksums()).doesNotContainKey(new RuleId(SLAB));
        assertThat(compiled.files().get("law/rates.json")).doesNotContain(SLAB);
    }

    @Test
    void compile_whenAVersionLiesOutsideTheYear_leavesItOutOfThatYearOnly() throws IOException {
        // Given — a 2024-25 version before the 2026-27 one
        Path root = timelineCopy();
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        ObjectNode earlier = (ObjectNode) rule.path("versions").get(0).deepCopy();
        ((ObjectNode) earlier.path("validity")).put("from", "2024-04-01").put("to", "2025-03-31");
        ((ArrayNode) rule.path("versions")).insert(0, earlier);
        write(root.resolve(SLAB_FILE), rule);
        Timeline timeline = TimelineReader.read(root);

        // When
        JsonNode in2026 = compiledRule(RulePackCompiler.compile(timeline, TY_2026));
        JsonNode in2024 = compiledRule(RulePackCompiler.compile(timeline, new TaxYearId(2024)));

        // Then
        assertThat(in2026.path("versions")).hasSize(1);
        assertThat(in2026.path("versions").get(0).path("validity").path("from").asString()).isEqualTo("2026-04-01");
        assertThat(in2024.path("versions")).hasSize(1);
        assertThat(in2024.path("versions").get(0).path("validity").path("from").asString()).isEqualTo("2024-04-01");
    }

    @Test
    void compile_whenAVersionIsPendingNotification_keepsIt() throws IOException {
        // Given — pending versions have no dates, and I16 needs their interim rule in the pack
        Path root = timelineCopy();
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        ObjectNode pending = (ObjectNode) rule.path("versions").get(0).deepCopy();
        pending.set("validity", MAPPER.createObjectNode()
                .put("type", "PendingNotification")
                .put("interimRule", "RATE-SLAB-FIRST-SCHEDULE")
                .put("interimDescription", "the first-schedule rates apply meanwhile"));
        pending.put("legalStatus", "PENDING_NOTIFICATION");
        ((ArrayNode) rule.path("versions")).add(pending);
        write(root.resolve(SLAB_FILE), rule);

        // When
        JsonNode compiled = compiledRule(RulePackCompiler.compile(TimelineReader.read(root), TY_2026));

        // Then
        assertThat(compiled.path("versions")).hasSize(2);
        assertThat(compiled.path("versions").get(1).path("legalStatus").asString()).isEqualTo("PENDING_NOTIFICATION");
    }

    @Test
    void compile_whenOnlyANoteChanges_changesNoChecksum() throws IOException {
        // Given
        Path root = timelineCopy();
        ObjectNode regime = (ObjectNode) tree(root.resolve("law/regimes/NEW_TAX_REGIME.json"));
        regime.put("_pendingRules", "an edited note");
        write(root.resolve("law/regimes/NEW_TAX_REGIME.json"), regime);
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        rule.put("_note", "a new note");
        write(root.resolve(SLAB_FILE), rule);

        // When
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(root), TY_2026);

        // Then
        assertThatCode(() -> lock().verify(compiled)).doesNotThrowAnyException();
    }

    private static RulePackLock lock() throws IOException {
        return RulePackLock.read(TY_2026, Files.readString(LOCK), LOCK.toString());
    }

    private static JsonNode compiledRule(CompiledPack pack) {
        for (JsonNode rule : tree(pack.files().get("law/rates.json")).path("rules")) {
            if (SLAB.equals(rule.path("ruleId").asString())) {
                return rule;
            }
        }
        throw new AssertionError(SLAB + " is not in the compiled pack");
    }

    /** Today's files list identities in authoring order; the compiler lists them by id. */
    private static JsonNode identitiesInOrder(JsonNode file) {
        Map<String, String> arraysAndKeys = Map.of("rules", "ruleId", "regimes", "code", "nodes", "nodeId");
        ObjectNode ordered = (ObjectNode) file.deepCopy();
        arraysAndKeys.forEach((array, key) -> {
            if (ordered.has(array)) {
                List<JsonNode> elements = new ArrayList<>();
                ordered.path(array).forEach(elements::add);
                elements.sort(Comparator.comparing(element -> element.path(key).asString()));
                ArrayNode sorted = MAPPER.createArrayNode();
                elements.forEach(sorted::add);
                ordered.set(array, sorted);
            }
        });
        return ordered;
    }

    private static GoverningInstruments instruments() {
        Instrument act = Instrument.assented(
                "Income-tax Act, 2025 (30 of 2025)",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-22082025-265620",
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                VerificationLevel.SOURCE_CHECKED);
        return new GoverningInstruments(
                act,
                act,
                Instrument.notified(
                        "Income-tax Rules, 2026",
                        LocalDate.of(2026, 4, 1),
                        "CG-DL-E-20032026-271092",
                        URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                        VerificationLevel.SOURCE_CHECKED));
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

    private static JsonNode tree(Path file) {
        try {
            return MAPPER.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static String text(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static JsonNode tree(String content) {
        return MAPPER.readTree(content);
    }

    private static void write(Path file, JsonNode node) throws IOException {
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }
}
