package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.RegistryJson;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A year's rule pack compiled from the timeline (D-034, D-054): pinned by its lock, and holding only
 * published versions that touch the year. That the compiled 2026-27 pack equalled the per-year copy it
 * replaced was proved before the copy was removed ({@code W-045g}, {@code W-045g3}).
 */
class RulePackCompilerTest {

    private static final TaxYearId TY_2026 = new TaxYearId(2026);
    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final Path LOCK = Path.of("src/main/resources/rulepacks/2026-27.lock.json");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String SLAB = "RATE-SLAB-NEW-TAX-REGIME";
    private static final String SLAB_FILE = "law/rules/" + SLAB + ".json";

    /** The rule's last version — the one in force in 2026-27, since TY 2025-26 has its own before it. */
    private static JsonNode inForceIn2026(ObjectNode rule) {
        JsonNode versions = rule.path("versions");
        return versions.get(versions.size() - 1);
    }

    @TempDir
    Path copy;

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
        ObjectNode band = (ObjectNode) inForceIn2026(rule).path("cap").path("bands").get(1);
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
        ((ObjectNode) inForceIn2026(rule)).put("publicationStatus", "DRAFT");
        write(root.resolve(SLAB_FILE), rule);

        // When
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(root), TY_2026);

        // Then
        assertThat(compiled.ruleChecksums()).doesNotContainKey(new RuleId(SLAB));
        assertThat(compiled.files().get("law/rates.json")).doesNotContain(SLAB);
    }

    @Test
    void compile_whenARegimeHasNoElectionInTheYear_isRefused() throws IOException {
        // When / Then — the shipped elections start at 2025-26; the regimes are in force from 2024-25 (D-133)
        Timeline timeline = TimelineReader.read(timelineCopy());
        assertThatThrownBy(() -> RulePackCompiler.compile(timeline, new TaxYearId(2024)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("0 election versions are in force in 2024-25");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void compile_whenTheShippedTimelineIsCompiled_holdsTheYearsVersionOfEachDisallowanceSet(int year) {
        // When — D-165: the set file holds a version per span of years
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(TIMELINE), TaxYearId.of(year));

        // Then — one plain set, its year's provision, no versions or validity
        JsonNode sets = MAPPER.readTree(compiled.files().get("law/regimes.json")).path("disallowanceSets");
        assertThat(sets).hasSize(1);
        JsonNode set = sets.get(0);
        assertThat(set.path("id").asString()).isEqualTo("DIS-NEW-TAX-REGIME-NO-EXEMPTION");
        assertThat(set.path("displayName").asString()).contains(year == 2025 ? "s.115BAC(2)(i)" : "s.202(2)(a)");
        assertThat(set.has("versions")).isFalse();
        assertThat(set.has("validity")).isFalse();
    }

    @Test
    void read_whenTwoVersionsOfADisallowanceSetOverlap_isRefused() throws IOException {
        // Given — the TY 2026-27 version moved back into TY 2025-26
        Path root = timelineCopy();
        Path file = root.resolve("law/disallowances/DIS-NEW-TAX-REGIME-NO-EXEMPTION.json");
        ObjectNode set = (ObjectNode) tree(file);
        ((ObjectNode) set.path("versions").get(1).path("validity")).put("from", "2026-01-01");
        write(file, set);

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("law/disallowances/DIS-NEW-TAX-REGIME-NO-EXEMPTION.json: versions overlap");
    }

    @Test
    void read_whenADisallowanceSetFileIsNamedForAnotherId_isRefused() throws IOException {
        // Given
        Path root = timelineCopy();
        Path file = root.resolve("law/disallowances/DIS-NEW-TAX-REGIME-NO-EXEMPTION.json");
        ObjectNode set = (ObjectNode) tree(file);
        set.put("id", "DIS-SOMETHING-ELSE");
        write(file, set);

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("holds DIS-SOMETHING-ELSE; a timeline file is named for the one identity it holds");
    }

    @Test
    void read_whenTwoElectionVersionsOverlap_isRefused() throws IOException {
        // Given — the 2026-27 version moved back into 2025-26
        Path root = timelineCopy();
        Path file = root.resolve("law/regimes/FIRST_SCHEDULE_RATES.json");
        ObjectNode spec = (ObjectNode) tree(file);
        ((ObjectNode) spec.path("elections").get(1).path("validity")).put("from", "2026-01-01");
        write(file, spec);

        // When / Then
        assertThatThrownBy(() -> TimelineReader.read(root))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("election versions overlap");
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
        // The copy's regimes need an election in 2024-25 too (D-133); the shipped ones start at 2025-26
        for (String regime : List.of("NEW_TAX_REGIME", "FIRST_SCHEDULE_RATES")) {
            Path file = root.resolve("law/regimes/" + regime + ".json");
            ObjectNode spec = (ObjectNode) tree(file);
            ((ObjectNode) spec.path("elections").get(0).path("validity")).put("from", "2024-04-01");
            write(file, spec);
        }
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
    void compile_whenAComponentIsLiveInTheYear_emitsItAsTheRegistryReadsIt() throws IOException {
        // Given
        Path root = timelineCopy();
        ComponentFiles.write(root, "FIXTURE_PAY", ComponentFiles.component("FIXTURE_PAY", "2025-04-01", Optional.empty()));

        // When
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(root), TY_2026);

        // Then — the file the assembler reads holds it, in the shape RegistryJson loads
        Map<ComponentCode, ComponentDefinition> registry = RegistryJson.components(
                tree(compiled.files().get("exec/components.json")), "exec/components.json");
        assertThat(registry).containsOnlyKeys(new ComponentCode("FIXTURE_PAY"), new ComponentCode("BASIC"), new ComponentCode("BONUS"), new ComponentCode("CHILDREN_EDUCATION_ALLOWANCE"), new ComponentCode("COMMISSION"), new ComponentCode("CONVEYANCE_ALLOWANCE"), new ComponentCode("DEARNESS_ALLOWANCE"), new ComponentCode("ENTERTAINMENT_ALLOWANCE"), new ComponentCode("HOSTEL_ALLOWANCE"), new ComponentCode("HRA"), new ComponentCode("LTA"), new ComponentCode("PROFESSIONAL_TAX"), new ComponentCode("SPECIAL_ALLOWANCE"), new ComponentCode("TRANSPORT_ALLOWANCE_DISABLED"), new ComponentCode("UNIFORM_ALLOWANCE"), new ComponentCode("GRATUITY"), new ComponentCode("LEAVE_ENCASHMENT"), new ComponentCode("COMMUTED_PENSION"), new ComponentCode("VRS_COMPENSATION"), new ComponentCode("RETRENCHMENT_COMPENSATION"), new ComponentCode("NOTICE_PAY"), new ComponentCode("DEFERRED_ANNUITY"), new ComponentCode("PROVIDENT_FUNDS_ACT_FUND"), new ComponentCode("NOTIFIED_PROVIDENT_FUND"), new ComponentCode("EMPLOYEE_PF"), new ComponentCode("EMPLOYEE_SUPERANNUATION"), new ComponentCode("NOTIFIED_DEPOSIT_SCHEME"), new ComponentCode("SAVINGS_CERTIFICATE"), new ComponentCode("MUTUAL_FUND_UNITS"), new ComponentCode("MUTUAL_FUND_PENSION_FUND"), new ComponentCode("NATIONAL_HOUSING_BANK_SCHEME"), new ComponentCode("HOUSING_FINANCE_DEPOSIT"), new ComponentCode("TUITION_FEES"), new ComponentCode("HOUSE_PURCHASE_PAYMENT"), new ComponentCode("BANK_TERM_DEPOSIT"), new ComponentCode("NABARD_BONDS"), new ComponentCode("SENIOR_CITIZEN_SAVINGS"), new ComponentCode("POST_OFFICE_TIME_DEPOSIT"), new ComponentCode("GOVERNMENT_PENSION_ADDITIONAL"), new ComponentCode("PENSION_FUND_ANNUITY"), new ComponentCode("EMPLOYEE_NPS"), new ComponentCode("ELIGIBLE_ISSUE_SUBSCRIPTION"), new ComponentCode("NPS_MINOR_DEPOSIT"), new ComponentCode("LIFE_INSURANCE_PREMIUM"), new ComponentCode("HEALTH_INSURANCE_FAMILY"), new ComponentCode("HEALTH_INSURANCE_PARENTS"), new ComponentCode("MEDICAL_EXPENDITURE_FAMILY"), new ComponentCode("MEDICAL_EXPENDITURE_PARENTS"), new ComponentCode("GOVERNMENT_DEFERRED_ANNUITY"), new ComponentCode("UNIT_LINKED_INSURANCE_PLAN"), new ComponentCode("MUTUAL_FUND_UNIT_LINKED_PLAN"), new ComponentCode("ANNUITY_PLAN"));
    }

    @Test
    void compile_whenAComponentIsWithdrawnBeforeTheYear_leavesItOutOfThatYearOnly() throws IOException {
        // Given — live for TY 2025-26 only
        Path root = timelineCopy();
        ComponentFiles.write(root, "FIXTURE_PAY", ComponentFiles.component("FIXTURE_PAY", "2025-04-01", Optional.of("2026-03-31")));
        Timeline timeline = TimelineReader.read(root);

        // When
        String in2026 = RulePackCompiler.compile(timeline, TY_2026).files().get("exec/components.json");
        String in2025 = RulePackCompiler.compile(timeline, new TaxYearId(2025)).files().get("exec/components.json");

        // Then
        assertThat(in2026).doesNotContain("FIXTURE_PAY");
        assertThat(in2025).contains("FIXTURE_PAY");
    }

    @Test
    void compile_whenAComponentNamesARuleWithNoVersionInTheYear_dropsThatReferenceInThatYearOnly() throws IOException {
        // Given — D-105: the distance rate is stated for TY 2026-27 only
        Path root = timelineCopy();
        ObjectNode pay = ComponentFiles.component("FIXTURE_PAY", "2025-04-01", Optional.empty());
        ((ArrayNode) pay.path("feedsInto")).add("SAL-LEAVE-TRAVEL-DISTANCE-RATE");
        ((ArrayNode) pay.path("salaryBaseFor")).addObject()
                .put("rule", "SAL-LEAVE-TRAVEL-DISTANCE-RATE").put("onlyWhereTermsProvide", false);
        ComponentFiles.write(root, "FIXTURE_PAY", pay);
        Timeline timeline = TimelineReader.read(root);

        // When
        JsonNode in2025 = compiledComponent(RulePackCompiler.compile(timeline, new TaxYearId(2025)), "FIXTURE_PAY");
        JsonNode in2026 = compiledComponent(RulePackCompiler.compile(timeline, TY_2026), "FIXTURE_PAY");

        // Then
        assertThat(in2025.path("feedsInto").toString()).doesNotContain("SAL-LEAVE-TRAVEL-DISTANCE-RATE")
                .contains(SLAB);
        assertThat(in2025.path("salaryBaseFor")).isEmpty();
        assertThat(in2026.path("feedsInto").toString()).contains("SAL-LEAVE-TRAVEL-DISTANCE-RATE");
        assertThat(in2026.path("salaryBaseFor")).hasSize(1);
    }

    @Test
    void compile_whenAComponentNamesARuleNoYearStates_isRefusedNamingIt() throws IOException {
        // Given — a misspelt rule, which dropping per year would otherwise hide (D-105)
        Path root = timelineCopy();
        ObjectNode pay = ComponentFiles.component("FIXTURE_PAY", "2025-04-01", Optional.empty());
        ((ArrayNode) pay.path("feedsInto")).add("SAL-NO-SUCH-RULE");
        ComponentFiles.write(root, "FIXTURE_PAY", pay);
        Timeline timeline = TimelineReader.read(root);

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(timeline, TY_2026))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("FIXTURE_PAY")
                .hasMessageContaining("SAL-NO-SUCH-RULE");
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

    private static JsonNode compiledComponent(CompiledPack compiled, String code) {
        for (JsonNode component : tree(compiled.files().get("exec/components.json")).path("components")) {
            if (component.path("code").asString().equals(code)) {
                return component;
            }
        }
        throw new AssertionError(code + " is not in the compiled pack");
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

    private static JsonNode tree(String content) {
        return MAPPER.readTree(content);
    }

    private static void write(Path file, JsonNode node) throws IOException {
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }
}
