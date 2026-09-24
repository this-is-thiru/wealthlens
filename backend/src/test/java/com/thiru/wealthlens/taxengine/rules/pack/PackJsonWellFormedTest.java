package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real TY 2026-27 pack, checked as data.
 *
 * <p>This is the highest-risk file set in the phase: every figure in it was transcribed by hand from
 * a gazette PDF, and a transcription error here is a wrong tax bill that no downstream test would
 * attribute to the pack. So the checks are about the figures and the shape, not about the loader —
 * {@code W-034} exercises the loader end to end.
 */
class PackJsonWellFormedTest {

    private static final String ROOT = "rulepacks/2026-27";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static String read(String path) {
        try (InputStream in = PackJsonWellFormedTest.class.getClassLoader()
                .getResourceAsStream(ROOT + "/" + path)) {
            assertThat(in).as("%s is on the classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private static JsonNode json(String path) {
        return MAPPER.readTree(read(path));
    }

    @Nested
    @DisplayName("every file parses")
    class WellFormed {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "manifest.json",
            "law/rates.json",
            "law/regimes.json",
            "exec/pipelines.json",
            "exec/components.json",
            "forms/itr-forms.json",
            "content/explanations.json"
        })
        void parses(String path) {
            assertThat(json(path)).isNotNull();
        }
    }

    @Nested
    @DisplayName("the manifest declares four layers, and the checksums are real")
    class Manifest {

        private static final Map<String, List<String>> LAYER_FILES = Map.of(
                "law", List.of("law/rates.json", "law/regimes.json"),
                "exec", List.of("exec/pipelines.json", "exec/components.json"),
                "forms", List.of("forms/itr-forms.json"),
                "content", List.of("content/explanations.json"));

        @Test
        void allFourLayersAreVersioned() {
            JsonNode layers = json("manifest.json").get("layers");

            assertThat(layers.propertyNames())
                    .containsExactlyInAnyOrder("law", "exec", "forms", "content");
        }

        @Test
        void eachLayersChecksumMatchesItsBytes() {
            // Written by hashing the files, not typed as plausible hex — otherwise this test
            // would prove only that a string was copied correctly
            JsonNode layers = json("manifest.json").get("layers");

            LAYER_FILES.forEach((layer, files) -> {
                String body = files.stream().map(PackJsonWellFormedTest::read)
                        .reduce("", String::concat);
                assertThat(layers.get(layer).get("checksum").asText())
                        .as("%s checksum", layer)
                        .isEqualTo("sha256:" + sha256(body));
            });
        }

        @Test
        void theThreeGoverningInstrumentsArePresent() {
            // YR-ACT-002/-003/-004. The Finance Act is where RATE-SUR-* lives.
            JsonNode instruments = json("manifest.json").get("instruments");

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
            assertThat(json("manifest.json").get("instruments").get("rules").has("assentedOn"))
                    .isFalse();
        }

        private static String sha256(String content) {
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
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
            for (JsonNode rule : json("law/rates.json").get("rules")) {
                for (JsonNode version : rule.get("versions")) {
                    JsonNode bands = version.get("cap").get("bands");
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

        private static void assertBoundIsRound(JsonNode rule, JsonNode bound) {
            if (bound == null || bound.isNull()) {
                return;
            }
            String value = bound.asText();
            assertThat(value.equals("0") || value.endsWith("0000"))
                    .as("%s band bound %s — a bound ending in 1 is the 400001 trap",
                            rule.get("ruleId").asText(), value)
                    .isTrue();
        }

        @Test
        void bandsAreContiguousWithNoGapAndNoOverlap() {
            for (JsonNode rule : json("law/rates.json").get("rules")) {
                for (JsonNode version : rule.get("versions")) {
                    JsonNode bands = version.get("cap").get("bands");
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
            for (JsonNode rule : json("law/rates.json").get("rules")) {
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
            for (JsonNode rule : json("law/rates.json").get("rules")) {
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
            for (JsonNode node : json("law/rates.json").findValues("provenance")) {
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
            for (JsonNode regime : json("law/regimes.json").get("regimes")) {
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
            json("law/rates.json").get("rules").forEach(r -> ruleIds.add(r.get("ruleId").asText()));

            for (JsonNode regime : json("law/regimes.json").get("regimes")) {
                assertThat(ruleIds)
                        .as("%s rate table", regime.get("code").asText())
                        .contains(regime.get("rateTable").asText());
            }
        }

        @Test
        void theOptOutRidesOnTheReturnWithNoForm() {
            // Rule 136, VERIFIED. Naming a form would send the taxpayer after one that does not exist.
            for (JsonNode regime : json("law/regimes.json").get("regimes")) {
                if ("FIRST_SCHEDULE_RATES".equals(regime.get("code").asText())) {
                    JsonNode election = regime.get("election");
                    assertThat(election.get("vehicle").asText()).isEqualTo("IN_THE_RETURN");
                    assertThat(election.has("form")).isFalse();
                    assertThat(election.get("deadline").get("section").asText()).isEqualTo("263(1)");
                }
            }
        }

        @Test
        void theDefaultRegimeRequiresNoAction() {
            for (JsonNode regime : json("law/regimes.json").get("regimes")) {
                if (regime.get("isDefault").asBoolean()) {
                    assertThat(regime.get("election").get("kind").asText())
                            .isEqualTo("DEFAULT_NO_ACTION");
                }
            }
        }
    }
}
