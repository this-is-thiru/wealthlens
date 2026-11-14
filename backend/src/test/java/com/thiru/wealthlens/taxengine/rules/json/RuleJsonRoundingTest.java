package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.rounding.RuleRounding;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A rule version stating a rounding (D-078): read like a relief, at most one of cap, relief and
 * rounding, and a step that is a positive whole number of rupees. The figures are parse inputs checked
 * for round-trip, not law.
 */
class RuleJsonRoundingTest {

    private static final String PATH = "law/rules/X.json.versions[0]";

    private static final String REST = """
            "validity": { "type": "Between", "from": "2026-04-01" },
            "regimes": { "type": "AllRegimes" },
            "provision1961": { "instrument": "Income-tax Act, 1961", "reference": "s.288A", "presence": "PRESENT" },
            "provision2025": { "instrument": "Income-tax Act, 2025", "reference": "s.516", "presence": "PRESENT" },
            "introducedBy": {
              "instrumentCitation": "Income-tax Act, 2025 (30 of 2025), s.516",
              "gazetteRef": "CG-DL-E-22082025-265620",
              "notifiedOn": "2025-08-21", "effectiveFrom": "2026-04-01",
              "retrospective": false,
              "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
              "verificationLevel": "SOURCE_CHECKED" },
            "legalStatus": "IN_FORCE", "publicationStatus": "PUBLISHED",
            "provenance": {
              "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
              "sourceDescription": "Gazette CG-DL-E-22082025-265620, s.516",
              "accessedOn": "2026-09-25", "verificationLevel": "SOURCE_CHECKED" }""";

    private static final String TO_TEN = """
            "rounding": { "toNearest": "10", "roundsUpFrom": "5" }""";

    private static RuleVersion versionWith(String extra) {
        return RuleJson.version(PackJsonTest.json("{ " + extra + ", " + REST + " }"), PATH);
    }

    @Test
    void version_whenRoundingToTen_readsTheStep() {
        // When
        RuleVersion version = versionWith(TO_TEN);

        // Then
        assertThat(version.rounding()).contains(new RuleRounding(Money.of("10"), Money.of("5")));
        assertThat(version.cap()).isEmpty();
        assertThat(version.relief()).isEmpty();
    }

    @Test
    void version_whenRoundingWithoutConditions_loads() {
        // When
        RuleVersion version = versionWith(TO_TEN);

        // Then
        assertThat(version.conditions()).isEmpty();
    }

    @Test
    void version_whenCapOnly_hasNoRounding() {
        // When
        RuleVersion version = versionWith("""
                "cap": { "type": "Fixed", "amount": "0" }""");

        // Then
        assertThat(version.rounding()).isEmpty();
    }

    @Test
    void version_whenCapAndRounding_isRefusedNamingThePath() {
        // Given
        String both = """
                "cap": { "type": "Fixed", "amount": "0" },
                """ + TO_TEN;

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("a cap")
                .hasMessageContaining("a rounding");
    }

    @Test
    void version_whenReliefAndRounding_isRefusedNamingThePath() {
        // Given
        String both = """
                "relief": { "type": "NoRelief", "limitedToSlabRateTax": false },
                """ + TO_TEN;

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("a relief")
                .hasMessageContaining("a rounding");
    }

    @Test
    void version_whenToNearestAbsent_isRefused() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "rounding": { }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".rounding.toNearest is required and is absent");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10", "10.50"})
    void version_whenToNearestIsNotAPositiveWholeRupeeAmount_isRefused(String step) {
        // When / Then — a valid round-up point, so the step alone is wrong
        assertThatThrownBy(() -> versionWith("""
                        "rounding": { "toNearest": "%s", "roundsUpFrom": "5" }""".formatted(step)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("positive whole number of rupees");
    }

    @Test
    void version_whenNeitherCapReliefRoundingNorConditions_isRefused() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "conditions": []"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("has no cap, relief, rounding, definition or conditions");
    }

    @ParameterizedTest(name = "roundsUpFrom {0}")
    @ValueSource(strings = {"0", "11", "4.50"})
    void version_whenTheRoundUpPointIsNotAWholeRupeeWithinTheStep_isRefusedNamingThePath(String upFrom) {
        // When / Then — nil, above the ₹10 step, or with paise (D-316)
        assertThatThrownBy(() -> versionWith("""
                        "rounding": { "toNearest": "10", "roundsUpFrom": "%s" }""".formatted(upFrom)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("round-up point");
    }

    @Test
    void version_whenTheRoundUpPointIsMissing_isRefusedNamingTheField() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "rounding": { "toNearest": "10" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("roundsUpFrom");
    }
}
