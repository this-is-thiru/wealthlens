package com.thiru.wealthlens.taxengine.rules.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The coverage register (D-038, D-059, D-066) — the build's answer to "which sections do we model". This is
 * the CI gate of {@code 00-BRIEF.md} §7.1, run against the shipped timeline.
 */
@Log4j2
class CoverageRegisterTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String SLAB_FILE = "law/rules/RATE-SLAB-NEW-TAX-REGIME.json";
    private static final String COVERAGE = "content/coverage.json";
    private static final String ACT_2025 = "content/acts/INCOME_TAX_ACT_2025.json";
    private static final String ACT_1961 = "content/acts/INCOME_TAX_ACT_1961.json";

    @TempDir
    Path copy;

    @Test
    void verify_whenTheShippedTimelineIsChecked_passes() {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);

        // When / Then
        assertThatCode(() -> CoverageRegister.of(timeline).verify(timeline)).doesNotThrowAnyException();
    }

    @Test
    void verify_whenARuleCitesASectionTheRegisterDoesNotKnow_isRefusedNamingBoth() {
        // Given
        Path root = edited(SLAB_FILE, rule ->
                ((ObjectNode) rule.path("versions").get(0).path("provision2025")).put("reference", "s.999(1)"));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME")
                .hasMessageContaining("Income-tax Act, 2025 s.999");
    }

    @Test
    void verify_whenAModelledRuleHasNoPublishedVersion_isRefused() {
        // Given
        Path root = edited(SLAB_FILE, rule ->
                ((ObjectNode) rule.path("versions").get(0)).put("publicationStatus", "DRAFT"));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME")
                .hasMessageContaining("no PUBLISHED version");
    }

    @Test
    void verify_whenAModelledEntryNamesARuleTheTimelineLacks_isRefused() {
        // Given
        Path root = edited(COVERAGE, register ->
                ((ArrayNode) entry((ArrayNode) register.path("sections"), "INCOME_TAX_ACT_2025", "202").path("rules"))
                        .add("RATE-SLAB-GHOST"));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("RATE-SLAB-GHOST")
                .hasMessageContaining("not in the timeline");
    }

    @Test
    void of_whenAnEntryHasNoClassification_isRefused() {
        // Given
        Path root = edited(COVERAGE, register -> ((ObjectNode) register.path("sections").get(0)).remove("classification"));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("classification");
    }

    @Test
    void of_whenASectionIsListedTwice_isRefused() {
        // Given — two entries for one section would be two classifications
        Path root = edited(COVERAGE, register -> {
            ArrayNode sections = (ArrayNode) register.path("sections");
            sections.add(sections.get(0).deepCopy());
        });
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("listed twice");
    }

    @Test
    void of_whenAPlannedEntryNamesNoBacklogItem_isRefused() {
        // Given
        Path root = edited(COVERAGE, register -> ((ArrayNode) register.path("sections")).add(MAPPER.createObjectNode()
                .put("act", "INCOME_TAX_ACT_2025")
                .put("section", "19")
                .put("classification", "PLANNED")
                .set("assesseeTypes", MAPPER.createArrayNode().add("INDIVIDUAL"))));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("backlogItem");
    }

    @Test
    void citedBy_whenAProvisionIsASectionOfAnIncomeTaxAct_isThatSection() {
        // When / Then
        assertThat(SectionRef.citedBy(Provision.present("Income-tax Act, 1961", "s.115BAC(1A)(iii)")))
                .contains(new SectionRef(SectionRef.Act.INCOME_TAX_ACT_1961, "115BAC"));
    }

    @Test
    void citedBy_whenAProvisionIsNotASectionOrNotPresent_isNothing() {
        // When / Then — schedules, other instruments and absent provisions cite no section
        assertThat(SectionRef.citedBy(Provision.present("Income-tax Act, 1961", "First Schedule Part I Paragraph A")))
                .isEmpty();
        assertThat(SectionRef.citedBy(Provision.present("Finance Act, 2026", "s.3(1)"))).isEmpty();
        assertThat(SectionRef.citedBy(Provision.absent("Income-tax Act, 2025"))).isEmpty();
    }

    @Test
    void countsByClassification_whenTheShippedRegisterIsRead_coverEverySectionOfEachListedAct() {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);
        int sectionsIn2025Act = MAPPER.readTree(timeline.acts().get("INCOME_TAX_ACT_2025")).path("sections").size();

        // When
        Map<SectionRef.Act, Map<String, Long>> counts = CoverageRegister.of(timeline).countsByClassification();
        log.info("Coverage register, sections by classification: {}", counts);

        // Then — every section of each Act in force is classified, once
        assertThat(counts.get(SectionRef.Act.INCOME_TAX_ACT_2025).values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(sectionsIn2025Act);
        assertThat(counts.get(SectionRef.Act.INCOME_TAX_ACT_1961).values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(inForce(timeline.acts().get("INCOME_TAX_ACT_1961")));
    }

    @Test
    void verify_whenARegisteredSectionIsOmittedFromTheAct_isRefusedNamingIt() {
        // Given — 80C marked omitted in the Act's list while the register still classifies it
        Path root = edited(ACT_1961, act -> ((ObjectNode) entry((ArrayNode) act.path("sections"), null, "80C"))
                .put("omitted", true));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("Income-tax Act, 1961 s.80C")
                .hasMessageContaining("omitted");
    }

    @Test
    void citedBy_whenASectionNumberCarriesAHyphen_keepsIt() {
        // When / Then — the Act writes 194-O so it cannot be read as 1940
        assertThat(SectionRef.citedBy(Provision.present("Income-tax Act, 1961", "s.194-O(1)")))
                .contains(new SectionRef(SectionRef.Act.INCOME_TAX_ACT_1961, "194-O"));
    }

    private static long inForce(String actContent) {
        JsonNode sections = MAPPER.readTree(actContent).path("sections");
        long count = 0;
        for (JsonNode section : sections) {
            count += section.path("omitted").asBoolean(false) ? 0 : 1;
        }
        return count;
    }

    @Test
    void verify_whenASectionOfTheActIsNotInTheRegister_isRefusedNamingIt() {
        // Given
        Path root = edited(COVERAGE, register -> removeSection((ArrayNode) register.path("sections"), "431"));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("Income-tax Act, 2025 s.431")
                .hasMessageContaining("not in the register");
    }

    @Test
    void verify_whenTheRegisterListsASectionTheActDoesNotHave_isRefused() {
        // Given
        Path root = edited(COVERAGE, register -> ((ArrayNode) register.path("sections")).add(MAPPER.createObjectNode()
                .put("act", "INCOME_TAX_ACT_2025")
                .put("section", "999")
                .put("classification", "NOT_COMPUTATIONAL")));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("Income-tax Act, 2025 s.999")
                .hasMessageContaining("not a section of the Act");
    }

    @Test
    void verify_whenTheActsSectionListSkipsANumber_isRefused() {
        // Given — a list with a gap is a bad parse, not an Act with a missing section
        Path root = edited(ACT_2025, act -> removeSection((ArrayNode) act.path("sections"), "250"));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(ACT_2025)
                .hasMessageContaining("not continuous")
                .hasMessageContaining("250");
    }

    @Test
    void of_whenADeferredEntryNamesNoScopeTier_isRefused() {
        // Given
        Path root = edited(COVERAGE, register -> ((ArrayNode) register.path("sections")).add(MAPPER.createObjectNode()
                .put("act", "INCOME_TAX_ACT_1961")
                .put("section", "44AD")
                .put("classification", "DEFERRED")
                .set("assesseeTypes", MAPPER.createArrayNode().add("INDIVIDUAL"))));
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> CoverageRegister.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("tier");
    }

    private static JsonNode entry(ArrayNode sections, String act, String section) {
        for (JsonNode entry : sections) {
            if ((act == null || act.equals(entry.path("act").asString()))
                    && section.equals(entry.path("section").asString())) {
                return entry;
            }
        }
        throw new IllegalStateException("no " + act + " s." + section + " in the register");
    }

    private static void removeSection(ArrayNode sections, String section) {
        for (int i = 0; i < sections.size(); i++) {
            if (section.equals(sections.get(i).path("section").asString())
                    && !"INCOME_TAX_ACT_1961".equals(sections.get(i).path("act").asString())) {
                sections.remove(i);
                return;
            }
        }
        throw new IllegalStateException("no section " + section + " to remove");
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
}
