package com.thiru.wealthlens.taxengine.rules.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.coverage.CoverageRegister;
import com.thiru.wealthlens.taxengine.rules.coverage.SectionRef;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The section catalogue (D-042, D-060) — SC-1 to SC-4 of {@code 00-BRIEF.md} §6.3, run against the
 * shipped timeline.
 */
class SectionCatalogueTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String ENTRY = "content/sections/SEC-DEFAULT-TAX-REGIME.json";

    /**
     * SC-1's ratchet for {@code PLANNED} sections: every section catalogued so far. It may only grow —
     * add a section here in the change that catalogues it; never remove one.
     */
    private static final Set<SectionRef> CATALOGUED = Set.of(
            new SectionRef(SectionRef.Act.INCOME_TAX_ACT_1961, "115BAC"),
            new SectionRef(SectionRef.Act.INCOME_TAX_ACT_2025, "202"),
            new SectionRef(SectionRef.Act.INCOME_TAX_ACT_1961, "80C"),
            new SectionRef(SectionRef.Act.INCOME_TAX_ACT_2025, "123"),
            new SectionRef(SectionRef.Act.INCOME_TAX_ACT_1961, "10"));

    @TempDir
    Path copy;

    @Test
    void verify_whenTheShippedTimelineIsChecked_passes() {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);

        // When / Then
        assertThatCode(() -> SectionCatalogue.of(timeline).verify(timeline, CoverageRegister.of(timeline)))
                .doesNotThrowAnyException();
    }

    @Test
    void catalogued_whenTheShippedCatalogueIsRead_keepsEverySectionCataloguedSoFar() {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);

        // When
        Set<SectionRef> catalogued = SectionCatalogue.of(timeline).catalogued();

        // Then
        assertThat(catalogued).as("SC-1 ratchet: a catalogued section lost its entry").containsAll(CATALOGUED);
    }

    @Test
    void catalogued_whenAnEntryIsRemoved_fallsBelowTheRatchet() {
        // Given
        Path root = edited(ENTRY, entry -> { });
        delete(root.resolve(ENTRY));

        // When
        Set<SectionRef> catalogued = SectionCatalogue.of(TimelineReader.read(root)).catalogued();

        // Then — the ratchet assertion above would fail on this catalogue
        assertThat(catalogued.containsAll(CATALOGUED)).isFalse();
        assertThat(catalogued).doesNotContain(new SectionRef(SectionRef.Act.INCOME_TAX_ACT_2025, "202"));
    }

    @Test
    void verify_whenAModelledSectionHasNoEntry_isRefusedNamingTheSection() {
        // Given
        Path root = edited(ENTRY, entry -> { });
        delete(root.resolve(ENTRY));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> SectionCatalogue.of(timeline).verify(timeline, CoverageRegister.of(timeline)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("Income-tax Act, 2025 s.202")
                .hasMessageContaining("MODELLED")
                .hasMessageContaining("no catalogue entry");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "You may deduct up to ₹ one lakh.",
        "You may deduct up to one lakh.",
        "Deduct twelve percent, i.e. 12 of it.",
        "Deduct a tenth (ten %).",
        "File by 31 July.",
        "Paid Rs. fifty.",
        "The limit is {{RATE-SLAB-NEW-TAX-REGIME}}."
    })
    void verify_whenTextTypesAFigureRateOrDate_isRefusedNamingTheField(String summary) {
        // Given
        Path root = edited(ENTRY, entry -> ((ObjectNode) entry).put("summary", summary));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> SectionCatalogue.of(timeline).verify(timeline, CoverageRegister.of(timeline)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("SEC-DEFAULT-TAX-REGIME")
                .hasMessageContaining("summary")
                .hasMessageContaining("SC-2");
    }

    @Test
    void verify_whenTextUsesAPlaceholderForARuleInTheTimeline_passes() {
        // Given
        Path root = edited(ENTRY, entry ->
                ((ObjectNode) entry).put("summary", "The slabs are {{RATE-SLAB-NEW-TAX-REGIME.bands}}."));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatCode(() -> SectionCatalogue.of(timeline).verify(timeline, CoverageRegister.of(timeline)))
                .doesNotThrowAnyException();
    }

    @Test
    void verify_whenAPlaceholderNamesARuleNotInTheTimeline_isRefused() {
        // Given
        Path root = edited(ENTRY, entry ->
                ((ObjectNode) entry).put("summary", "The ceiling is {{DED-INVEST-AGG.ceiling}}."));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> SectionCatalogue.of(timeline).verify(timeline, CoverageRegister.of(timeline)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("DED-INVEST-AGG")
                .hasMessageContaining("not in the timeline");
    }

    @Test
    void verify_whenARouteNamesARuleNotInTheTimeline_isRefused() {
        // Given
        Path root = edited(ENTRY, entry -> ((ObjectNode) entry.path("routes").get(0).path("conditions").get(0))
                .put("rule", "RATE-SLAB-GHOST"));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> SectionCatalogue.of(timeline).verify(timeline, CoverageRegister.of(timeline)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("routes[0]")
                .hasMessageContaining("RATE-SLAB-GHOST")
                .hasMessageContaining("not in the timeline");
    }

    @Test
    void of_whenADisqualifierNamesNoEnforcement_isRefused() {
        // Given
        Path root = edited(ENTRY, entry -> ((ObjectNode) entry.path("disqualifiers").get(0)).remove("enforcedBy"));

        // When / Then
        assertThatThrownBy(() -> SectionCatalogue.of(TimelineReader.read(root)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("disqualifiers[0]")
                .hasMessageContaining("enforcedBy");
    }

    @Test
    void of_whenAConditionIsNotOneOfTheAlgebrasRecords_isRefused() {
        // Given
        Path root = edited(ENTRY, entry -> ((ObjectNode) entry.path("disqualifiers").get(0))
                .set("enforcedBy", MAPPER.createObjectNode().put("type", "ByCondition").put("condition", "IsRich")));

        // When / Then
        assertThatThrownBy(() -> SectionCatalogue.of(TimelineReader.read(root)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("IsRich");
    }

    @Test
    void of_whenNotModelledGivesNoReason_isRefused() {
        // Given
        Path root = edited(ENTRY, entry -> ((ObjectNode) entry.path("disqualifiers").get(0))
                .set("enforcedBy", MAPPER.createObjectNode().put("type", "NotModelled").put("reason", " ")));

        // When / Then
        assertThatThrownBy(() -> SectionCatalogue.of(TimelineReader.read(root)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("disqualifiers[0]")
                .hasMessageContaining("reason");
    }

    @ParameterizedTest
    @ValueSource(strings = {"availability", "history"})
    void of_whenAnEntryAuthorsADerivedField_isRefused(String field) {
        // Given
        Path root = edited(ENTRY, entry -> ((ObjectNode) entry).put(field, "new regime only"));

        // When / Then
        assertThatThrownBy(() -> SectionCatalogue.of(TimelineReader.read(root)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(field)
                .hasMessageContaining("SC-4");
    }

    @Test
    void read_whenAnEntryFileIsNotNamedForItsKey_isRefused() {
        // Given
        Path root = edited(ENTRY, entry -> ((ObjectNode) entry).put("key", "SEC-SOMETHING-ELSE"));

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(ENTRY)
                .hasMessageContaining("SEC-SOMETHING-ELSE");
    }

    /** A copy of the shipped timeline with one file edited. */
    private Path edited(String file, Consumer<JsonNode> edit) {
        try (Stream<Path> files = Files.walk(TIMELINE)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path target = copy.resolve(TIMELINE.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
            JsonNode node = MAPPER.readTree(Files.readString(copy.resolve(file)));
            edit.accept(node);
            Files.writeString(copy.resolve(file), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare the timeline copy", e);
        }
        return copy;
    }

    private static void delete(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + file, e);
        }
    }
}
