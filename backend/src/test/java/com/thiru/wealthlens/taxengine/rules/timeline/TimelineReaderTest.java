package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
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
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String SLAB_FILE = "law/rules/RATE-SLAB-NEW-TAX-REGIME.json";

    /** Every rule the shipped timeline states, listed by hand; the lock, not this test, guards what is inside them. */
    private static final Set<RuleId> SHIPPED_RULES = Set.of(
            new RuleId("RATE-SLAB-NEW-TAX-REGIME"),
            new RuleId("RATE-SLAB-FIRST-SCHEDULE"),
            new RuleId("RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79"),
            new RuleId("RATE-SLAB-FIRST-SCHEDULE-AGE-80"),
            new RuleId("REBATE-NEW-TAX-REGIME"),
            new RuleId("REBATE-NEW-TAX-REGIME-EXCESS"),
            new RuleId("REBATE-RESIDENT-INDIVIDUAL"),
            new RuleId("RATE-ROUND-TOTAL-INCOME"),
            new RuleId("RATE-ROUND-PAYABLE"),
            new RuleId("RATE-HEALTH-EDUCATION-CESS"),
            new RuleId("RATE-SUR-FIRST-SCHEDULE"),
            new RuleId("RATE-SUR-FIRST-SCHEDULE-CEILING"),
            new RuleId("RATE-SUR-FIRST-SCHEDULE-EXCLUDING"),
            new RuleId("RATE-SUR-NEW-TAX-REGIME"),
            new RuleId("RATE-SUR-NEW-TAX-REGIME-CEILING"),
            new RuleId("RATE-SUR-NEW-TAX-REGIME-EXCLUDING"),
            new RuleId("SAL-SALARY-INCLUDES"),
            new RuleId("SAL-SALARY-FOR-HRA"),
            new RuleId("SAL-SALARY-FOR-GRATUITY"),
            new RuleId("SAL-SALARY-FOR-EMPLOYER-PENSION"),
            new RuleId("SAL-SALARY-FOR-EMPLOYEE-PENSION"),
            new RuleId("SAL-TAX-ON-EMPLOYMENT"),
            new RuleId("SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME"),
            new RuleId("SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE"),
            new RuleId("SAL-HOUSE-RENT-ALLOWANCE"),
            new RuleId("SAL-LEAVE-TRAVEL-CONCESSION"),
            new RuleId("SAL-LEAVE-TRAVEL-DISTANCE-RATE"),
            new RuleId("SAL-LEAVE-TRAVEL-JOURNEYS"),
            new RuleId("SAL-ALW-UNIFORM"),
            new RuleId("SAL-ALW-CONVEYANCE"),
            new RuleId("SAL-ALW-CHILDREN-EDUCATION"),
            new RuleId("SAL-ALW-HOSTEL"),
            new RuleId("SAL-ALW-TRANSPORT-DISABLED"),
            new RuleId("RET-COMMUTED-PENSION-FUND"),
            new RuleId("RET-COMMUTED-PENSION-GOVERNMENT"),
            new RuleId("RET-COMMUTED-PENSION-OTHER"),
            new RuleId("RET-GRATUITY-GOVERNMENT"),
            new RuleId("RET-LEAVE-ENCASHMENT-GOVERNMENT"),
            new RuleId("RET-RETRENCHMENT-APPROVED-SCHEME"),
            new RuleId("RET-VOLUNTARY-RETIREMENT"),
            new RuleId("SAL-ENTMT-ALLOWANCE"),
            new RuleId("SAL-SALARY-FOR-ENTMT"),
            new RuleId("RET-GRATUITY-OTHER"),
            new RuleId("RET-GRATUITY-AGGREGATE"),
            new RuleId("RET-LEAVE-ENCASHMENT-OTHER"),
            new RuleId("RET-LEAVE-ENCASHMENT-AGGREGATE"));

    @TempDir
    Path copy;

    @Test
    void read_whenTheShippedTimelineIsRead_holdsExactlyItsRulesRegimesAndNodes() {
        // When
        Timeline timeline = TimelineReader.read(REAL);

        // Then — listed by hand; the lock, not this test, guards the values inside them
        assertThat(timeline.rules().keySet()).containsExactlyInAnyOrderElementsOf(SHIPPED_RULES);
        assertThat(timeline.regimes().keySet())
                .containsExactlyInAnyOrder(new RegimeCode("NEW_TAX_REGIME"), new RegimeCode("FIRST_SCHEDULE_RATES"));
        assertThat(timeline.pipelineNodes().keySet())
                .extracting(NodeId::value)
                .containsExactlyInAnyOrder(
                        "inputs.components", "salary.gross", "salary.base", "salary.exemptions", "salary.retirement", "salary.deductions",
                        "salary.income",
                        "totalincome", "round.totalincome", "tax.bybasis",
                        "tax.afterrebate", "tax.surcharge", "tax.cess", "tax.netpayable", "round.netpayable");
    }

    @Test
    void read_whenTheShippedTimelineIsRead_holdsOnlyTheComponentsARuleReads() {
        // Then — a component arrives with the first rule that reads it (D-089): professional tax (D-093)
        assertThat(TimelineReader.read(REAL).components()).containsOnlyKeys(new ComponentCode("BASIC"), new ComponentCode("BONUS"), new ComponentCode("CHILDREN_EDUCATION_ALLOWANCE"), new ComponentCode("COMMISSION"), new ComponentCode("CONVEYANCE_ALLOWANCE"), new ComponentCode("DEARNESS_ALLOWANCE"), new ComponentCode("ENTERTAINMENT_ALLOWANCE"), new ComponentCode("HOSTEL_ALLOWANCE"), new ComponentCode("HRA"), new ComponentCode("LTA"), new ComponentCode("PROFESSIONAL_TAX"), new ComponentCode("SPECIAL_ALLOWANCE"), new ComponentCode("TRANSPORT_ALLOWANCE_DISABLED"), new ComponentCode("UNIFORM_ALLOWANCE"), new ComponentCode("GRATUITY"), new ComponentCode("LEAVE_ENCASHMENT"), new ComponentCode("COMMUTED_PENSION"), new ComponentCode("VRS_COMPENSATION"), new ComponentCode("RETRENCHMENT_COMPENSATION"), new ComponentCode("NOTICE_PAY"));
    }

    @Test
    void read_whenAComponentFileIsPresent_holdsItByItsCode() throws IOException {
        // Given
        Path root = realCopy();
        ComponentFiles.write(root, "FIXTURE_PAY", ComponentFiles.component("FIXTURE_PAY", "2025-04-01", Optional.empty()));

        // When
        Timeline timeline = TimelineReader.read(root);

        // Then
        assertThat(timeline.components())
                .containsOnlyKeys(new ComponentCode("FIXTURE_PAY"), new ComponentCode("BASIC"), new ComponentCode("BONUS"), new ComponentCode("CHILDREN_EDUCATION_ALLOWANCE"), new ComponentCode("COMMISSION"), new ComponentCode("CONVEYANCE_ALLOWANCE"), new ComponentCode("DEARNESS_ALLOWANCE"), new ComponentCode("ENTERTAINMENT_ALLOWANCE"), new ComponentCode("HOSTEL_ALLOWANCE"), new ComponentCode("HRA"), new ComponentCode("LTA"), new ComponentCode("PROFESSIONAL_TAX"), new ComponentCode("SPECIAL_ALLOWANCE"), new ComponentCode("TRANSPORT_ALLOWANCE_DISABLED"), new ComponentCode("UNIFORM_ALLOWANCE"), new ComponentCode("GRATUITY"), new ComponentCode("LEAVE_ENCASHMENT"), new ComponentCode("COMMUTED_PENSION"), new ComponentCode("VRS_COMPENSATION"), new ComponentCode("RETRENCHMENT_COMPENSATION"), new ComponentCode("NOTICE_PAY"));
        assertThat(timeline.components().get(new ComponentCode("FIXTURE_PAY")).cashNature()).isEqualTo(CashNature.CASH);
    }

    @Test
    void read_whenAComponentFileIsNamedForAnotherCode_isRefused() throws IOException {
        // Given
        Path root = realCopy();
        ComponentFiles.write(root, "OTHER_PAY", ComponentFiles.component("FIXTURE_PAY", "2025-04-01", Optional.empty()));

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("exec/components/OTHER_PAY.json")
                .hasMessageContaining("FIXTURE_PAY");
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
        // Given — in force 2023-24, lapsed for 2024-25, back from 2025-26: a gap, not an overlap
        Path root = realCopy();
        ObjectNode rule = (ObjectNode) tree(root.resolve(SLAB_FILE));
        ArrayNode versions = (ArrayNode) rule.path("versions");
        ObjectNode earlier = (ObjectNode) versions.get(0).deepCopy();
        ((ObjectNode) earlier.path("validity")).put("from", "2023-04-01").put("to", "2024-03-31");
        versions.insert(0, earlier);
        write(root.resolve(SLAB_FILE), rule);

        // When
        Timeline timeline = TimelineReader.read(root);

        // Then
        assertThat(timeline.rules().get(new RuleId("RATE-SLAB-NEW-TAX-REGIME")).versions()).hasSize(3);
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
