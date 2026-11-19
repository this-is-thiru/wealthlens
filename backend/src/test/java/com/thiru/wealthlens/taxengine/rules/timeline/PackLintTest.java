package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.PackLint.Finding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The cross-year lints (W-139, C2a). The shipped-pair lists were found by reading every rule file of the timeline: the two club
 * rows TY 2026-27 merged into {@code PERQ-CLUB-BUSINESS-UNIFORM} say nothing about the 2025 Act, and the three STT rows repeat
 * their instrument at 2026-04-01 ({@code plans/W-139.md}).
 */
class PackLintTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final TaxYearId TY_2025 = new TaxYearId(2025);
    private static final TaxYearId TY_2026 = new TaxYearId(2026);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String SLAB_FILE = "law/rules/RATE-SLAB-NEW-TAX-REGIME.json";

    @TempDir
    Path copy;

    private static List<Finding> shipped() {
        return PackLint.between(compile(TIMELINE, TY_2025), compile(TIMELINE, TY_2026));
    }

    @Test
    void between_whenTheShippedYearsAreCompared_findsTheTwoUnexplainedDropsOnly() {
        // When / Then — five more rules end in TY 2025-26, each saying the 2025 Act has no such provision
        assertThat(shipped()).filteredOn(Finding.UnexplainedDrop.class::isInstance).containsExactly(
                new Finding.UnexplainedDrop(new RuleId("PERQ-CLUB-BUSINESS")),
                new Finding.UnexplainedDrop(new RuleId("PERQ-CLUB-UNIFORM")));
    }

    @Test
    void between_whenTheShippedYearsAreCompared_findsTheThreeCopiedSttVersions() {
        // When / Then
        assertThat(shipped()).filteredOn(Finding.CopiedVersion.class::isInstance).containsExactly(
                new Finding.CopiedVersion(new RuleId("STT-PURCHASE-DELIVERY"), "2025-04-01", "2026-04-01"),
                new Finding.CopiedVersion(new RuleId("STT-SALE-DELIVERY"), "2025-04-01", "2026-04-01"),
                new Finding.CopiedVersion(new RuleId("STT-SALE-NONDELIVERY"), "2025-04-01", "2026-04-01"));
    }

    @Test
    void between_whenABandIsAddedToTheNextYearsSlab_findsTheShapeChange() throws IOException {
        // Given — the 2026-27 version's first band, 0 to 400000 at 0 %, split at 200000: seven bands become eight
        Path edited = editedSlab(rule -> {
            ArrayNode bands = (ArrayNode) last(rule).path("cap").path("bands");
            ObjectNode upper = (ObjectNode) bands.get(0).deepCopy();
            upper.put("lowerExclusive", "200000");
            ((ObjectNode) bands.get(0)).put("upperInclusive", "200000");
            bands.insert(1, upper);
        });

        // When
        List<Finding> findings = PackLint.between(compile(TIMELINE, TY_2025), compile(edited, TY_2026));

        // Then
        assertThat(findings)
                .filteredOn(finding -> finding instanceof Finding.ShapeChanged shape
                        && shape.rule().value().equals("RATE-SLAB-NEW-TAX-REGIME"))
                .singleElement().extracting(finding -> ((Finding.ShapeChanged) finding).field()).isEqualTo("cap.bands");
    }

    @Test
    void between_whenARateIsEditedTenfoldInTheSameYear_findsIt() throws IOException {
        // Given — band 1's rate 5 becomes 50
        Path edited = editedSlab(rule -> ((ObjectNode) last(rule).path("cap").path("bands").get(1)).put("rate", "50"));

        // When
        List<Finding> findings = PackLint.between(compile(TIMELINE, TY_2026), compile(edited, TY_2026));

        // Then
        assertThat(findings).containsExactly(new Finding.TenfoldChange(new RuleId("RATE-SLAB-NEW-TAX-REGIME"), "2026-04-01",
                "cap.bands[1].rate", "5", "50"));
    }

    @ParameterizedTest
    @CsvSource({"250000, 2500000, true", "250000, 300000, false", "0.04, 0.4, true", "0.4, 0.04, true", "0, 5, false",
        "NEW, OLD, false", "2025-04-01, 2026-04-01, false", "-1, -10, true"})
    void isTenfold_whenTwoValuesAreCompared_flagsTenTimesEitherWay(String before, String after, boolean expected) {
        // When / Then
        assertThat(PackLint.isTenfold(before, after)).isEqualTo(expected);
    }

    private static JsonNode last(JsonNode rule) {
        JsonNode versions = rule.path("versions");
        return versions.get(versions.size() - 1);
    }

    private static CompiledPack compile(Path root, TaxYearId year) {
        return RulePackCompiler.compile(TimelineReader.read(root), year);
    }

    private Path editedSlab(Consumer<JsonNode> edit) throws IOException {
        try (Stream<Path> files = Files.walk(TIMELINE)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path target = copy.resolve(TIMELINE.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy the timeline", e);
        }
        JsonNode rule = MAPPER.readTree(Files.readString(copy.resolve(SLAB_FILE)));
        edit.accept(rule);
        Files.writeString(copy.resolve(SLAB_FILE), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rule));
        return copy;
    }
}
