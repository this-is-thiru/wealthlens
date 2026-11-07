package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.RulePackCompiler;
import com.thiru.wealthlens.taxengine.rules.timeline.RulePackLock;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real law — the timeline's rule and regime files, and the 2026-27 lock — checked as data.
 *
 * <p>This is the highest-risk file set in the phase: every figure in it was transcribed by hand from
 * a gazette PDF, and a transcription error here is a wrong tax bill that no downstream test would
 * attribute to the data. So the checks are about the figures and the shape, not about the loader —
 * {@code RulePackLoadTest} exercises the loader end to end.
 */
class PackJsonWellFormedTest {

    private static final Path RULEPACKS = Path.of("src/main/resources/rulepacks");
    private static final Path TIMELINE = RULEPACKS.resolve("timeline");
    private static final Path LOCK = RULEPACKS.resolve("2026-27.lock.json");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static List<JsonNode> filesIn(String directory) {
        try (Stream<Path> files = Files.list(TIMELINE.resolve(directory))) {
            return files.sorted().map(file -> MAPPER.readTree(read(file))).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + directory, e);
        }
    }

    private static List<JsonNode> rules() {
        return filesIn("law/rules");
    }

    /** The protocol of the regime's election version that starts on {@code from} (D-133). */
    private static JsonNode electionFrom(JsonNode regime, String from) {
        for (JsonNode version : regime.get("elections")) {
            if (from.equals(version.get("validity").get("from").asText())) {
                return version.get("protocol");
            }
        }
        throw new IllegalStateException(regime.get("code").asText() + " has no election version from " + from);
    }

    private static List<JsonNode> regimes() {
        return filesIn("law/regimes");
    }

    private static JsonNode lock() {
        return MAPPER.readTree(read(LOCK));
    }

    @Nested
    @DisplayName("the lock declares four layers, and pins what the timeline compiles to")
    class Lock {

        @Test
        void allFourLayersAreVersioned() {
            assertThat(lock().get("layers").propertyNames())
                    .containsExactlyInAnyOrder("law", "exec", "forms", "content");
        }

        @Test
        void theLockMatchesTheCompiledTimeline() {
            // Written by compiling the timeline, not typed as plausible hex — otherwise this test
            // would prove only that a string was copied correctly
            TaxYearId year = new TaxYearId(2026);
            RulePackLock lock = RulePackLock.read(year, read(LOCK), LOCK.toString());

            assertThatCode(() -> lock.verify(RulePackCompiler.compile(TimelineReader.read(TIMELINE), year)))
                    .doesNotThrowAnyException();
        }

        @Test
        void theThreeGoverningInstrumentsArePresent() {
            // YR-ACT-002/-003/-004. The Finance Act is where RATE-SUR-* lives.
            JsonNode instruments = lock().get("instruments");

            assertThat(instruments.get("act").get("citation").asText())
                    .isEqualTo("Income-tax Act, 2025 (30 of 2025)");
            assertThat(instruments.get("financeAct").get("citation").asText())
                    .isEqualTo("Finance Act, 2026 (4 of 2026)");
            assertThat(instruments.get("rules").get("gazetteRef").asText())
                    .isEqualTo("CG-DL-E-20032026-271092");
        }

        @Test
        void theRulesInstrumentHasNoAssentDate() {
            // Subordinate legislation is notified, not assented
            assertThat(lock().get("instruments").get("rules").has("assentedOn")).isFalse();
        }
    }

    @Nested
    @DisplayName("the 400001 trap — RATE-SLAB-BAND-002")
    class BandBounds {

        @Test
        void everyBandBoundIsARoundFigure() {
            // The statute writes "From ₹400001 to ₹800000"; the arithmetic lower bound is 400000.
            // Storing lower + 1 loses one rupee of taxable width per band. This is H-5's first limb,
            // and the check is deliberately blunt: no bound may end in anything but four zeros.
            for (JsonNode rule : rules()) {
                for (JsonNode version : rule.get("versions")) {
                    JsonNode bands = slabBands(version);
                    if (bands == null) {
                        continue;
                    }
                    for (JsonNode band : bands) {
                        assertBoundIsRound(rule, band.get("lowerExclusive"));
                        assertBoundIsRound(rule, band.get("upperInclusive"));
                    }
                }
            }
        }

        /** A slab ladder's bands; a location cap's "bands" are places, not income bands (D-111). */
        private static JsonNode slabBands(JsonNode version) {
            JsonNode cap = version.path("cap");
            return "SlabLadder".equals(cap.path("type").asText()) ? cap.get("bands") : null;
        }

        /**
         * Statutory bounds that are right and not round: s.198(2)(a) / 1961 s.112A(2)(i) charge long-term gains
         * "exceeding ₹ 125000" (D-244). Each is named by rule and value, so the guard stays blunt for every other bound.
         */
        private static final Map<String, Set<String>> STATUTORY_BOUNDS =
                Map.of("RATE-CG-LONG-LISTED-EQUITY", Set.of("125000"));

        private static void assertBoundIsRound(JsonNode rule, JsonNode bound) {
            if (bound == null || bound.isNull()) {
                return;
            }
            String value = bound.asText();
            if (STATUTORY_BOUNDS.getOrDefault(rule.get("ruleId").asText(), Set.of()).contains(value)) {
                return;
            }
            assertThat(value.equals("0") || value.endsWith("0000"))
                    .as("%s band bound %s — a bound ending in 1 is the 400001 trap",
                            rule.get("ruleId").asText(), value)
                    .isTrue();
        }

        @Test
        void bandsAreContiguousWithNoGapAndNoOverlap() {
            for (JsonNode rule : rules()) {
                for (JsonNode version : rule.get("versions")) {
                    JsonNode bands = slabBands(version);
                    if (bands == null) {
                        continue;
                    }
                    assertThat(bands.get(0).get("lowerExclusive").asText())
                            .as("%s first band starts at zero", rule.get("ruleId").asText())
                            .isEqualTo("0");
                    for (int i = 1; i < bands.size(); i++) {
                        assertThat(bands.get(i).get("lowerExclusive").asText())
                                .as("%s band %d starts where band %d ended",
                                        rule.get("ruleId").asText(), i, i - 1)
                                .isEqualTo(bands.get(i - 1).get("upperInclusive").asText());
                    }
                    assertThat(bands.get(bands.size() - 1).get("upperInclusive").isNull())
                            .as("%s top band is open-ended", rule.get("ruleId").asText())
                            .isTrue();
                }
            }
        }
    }

    @Nested
    @DisplayName("the figures, against 01-rulebook.md R3")
    class Figures {

        private static JsonNode bandsOf(String ruleId) {
            for (JsonNode rule : rules()) {
                if (ruleId.equals(rule.get("ruleId").asText())) {
                    return rule.get("versions").get(0).get("cap").get("bands");
                }
            }
            throw new AssertionError("no rule " + ruleId);
        }

        @Test
        void theNewRegimeLadderIsSevenBandsAtNilFiveTenFifteenTwentyTwentyFiveThirty() {
            // 01-rulebook.md:82-92, VERIFIED (S2, s.202(1) Table)
            JsonNode bands = bandsOf("RATE-SLAB-NEW-TAX-REGIME");

            assertThat(bands).hasSize(7);
            assertThat(bands).extracting(b -> b.get("rate").asText())
                    .containsExactly("0", "5", "10", "15", "20", "25", "30");
            assertThat(bands).extracting(b -> b.get("lowerExclusive").asText())
                    .containsExactly("0", "400000", "800000", "1200000", "1600000", "2000000", "2400000");
        }

        @Test
        void theOldRegimeLadderIsFourBandsAtNilFiveTwentyThirty() {
            // 01-rulebook.md:104,115-120 — under 60. VERIFIED (S3, First Schedule Part I-B Para A)
            JsonNode bands = bandsOf("RATE-SLAB-FIRST-SCHEDULE");

            assertThat(bands).hasSize(4);
            assertThat(bands).extracting(b -> b.get("rate").asText())
                    .containsExactly("0", "5", "20", "30");
            assertThat(bands).extracting(b -> b.get("lowerExclusive").asText())
                    .containsExactly("0", "250000", "500000", "1000000");
        }

        @Test
        void everyRuleVersionNamesTheInstrumentThatIntroducedIt() {
            // Invariant I15, checked in the data as well as in the loader
            for (JsonNode rule : rules()) {
                for (JsonNode version : rule.get("versions")) {
                    assertThat(version.get("introducedBy"))
                            .as("%s introducedBy", rule.get("ruleId").asText())
                            .isNotNull();
                }
            }
        }

        @Test
        void everyProvenanceThatIsNotVerifiedSaysWhatToCheck() {
            // Invariant I1, in the data
            for (JsonNode node : rules().stream().flatMap(rule -> rule.findValues("provenance").stream()).toList()) {
                if ("UNVERIFIED".equals(node.get("verificationLevel").asText())) {
                    assertThat(node.has("whatToCheck")).isTrue();
                }
            }
        }
    }

    @Nested
    @DisplayName("regimes")
    class Regimes {

        @Test
        void exactlyOneRegimeIsDefault() {
            // Invariant I5, in the data
            long defaults = 0;
            for (JsonNode regime : regimes()) {
                if (regime.get("isDefault").asBoolean()) {
                    defaults++;
                }
            }
            assertThat(defaults).isEqualTo(1);
        }

        @Test
        void everyRateTableReferencedExists() {
            // Invariant I2, in the data
            java.util.Set<String> ruleIds = new java.util.HashSet<>();
            rules().forEach(r -> ruleIds.add(r.get("ruleId").asText()));

            for (JsonNode regime : regimes()) {
                assertThat(ruleIds)
                        .as("%s rate table", regime.get("code").asText())
                        .contains(regime.get("rateTable").asText());
            }
        }

        @Test
        void theOptOutRidesOnTheReturnWithNoForm() {
            // Rule 136, VERIFIED for TY 2026-27. Naming a form would send the taxpayer after one that does not exist.
            for (JsonNode regime : regimes()) {
                if ("FIRST_SCHEDULE_RATES".equals(regime.get("code").asText())) {
                    JsonNode election = electionFrom(regime, "2026-04-01");
                    assertThat(election.get("vehicle").asText()).isEqualTo("IN_THE_RETURN");
                    assertThat(election.has("form")).isFalse();
                    assertThat(election.get("deadline").get("section").asText()).isEqualTo("263(1)");
                }
            }
        }

        @Test
        void theDefaultRegimeRequiresNoAction() {
            for (JsonNode regime : regimes()) {
                if (regime.get("isDefault").asBoolean()) {
                    // Every year's election (D-133)
                    for (JsonNode version : regime.get("elections")) {
                        assertThat(version.get("protocol").get("kind").asText()).isEqualTo("DEFAULT_NO_ACTION");
                    }
                }
            }
        }
    }
}
