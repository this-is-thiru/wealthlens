package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.relief.ExcessOverThreshold;
import com.thiru.wealthlens.taxengine.rules.relief.FlatWithCeiling;
import com.thiru.wealthlens.taxengine.rules.relief.NoRelief;
import com.thiru.wealthlens.taxengine.rules.relief.RuleRelief;
import com.thiru.wealthlens.taxengine.rules.relief.ThresholdLadderCeiling;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A rule version carrying a relief (D-075): read like a cap, its limit to slab-rate tax always stated,
 * and never beside a cap. The figures are parse inputs checked for round-trip, not law.
 */
class RuleJsonReliefTest {

    private static final String PATH = "law/rules/X.json.versions[0]";

    private static final String REST = """
            "validity": { "type": "Between", "from": "2026-04-01" },
            "regimes": { "type": "OnlyIn", "regimes": ["NEW_TAX_REGIME"] },
            "provision1961": { "instrument": "Income-tax Act, 1961", "reference": "s.87A", "presence": "PRESENT" },
            "provision2025": { "instrument": "Income-tax Act, 2025", "reference": "s.156(2)(a)", "presence": "PRESENT" },
            "introducedBy": {
              "instrumentCitation": "Income-tax Act, 2025 (30 of 2025), s.156",
              "gazetteRef": "CG-DL-E-22082025-265620",
              "notifiedOn": "2025-08-21", "effectiveFrom": "2026-04-01",
              "retrospective": false,
              "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
              "verificationLevel": "SOURCE_CHECKED" },
            "legalStatus": "IN_FORCE", "publicationStatus": "PUBLISHED",
            "provenance": {
              "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
              "sourceDescription": "Gazette CG-DL-E-22082025-265620, s.156",
              "accessedOn": "2026-09-25", "verificationLevel": "SOURCE_CHECKED" }""";

    private static RuleVersion versionWith(String extra) {
        return RuleJson.version(PackJsonTest.json("{ " + extra + ", " + REST + " }"), PATH);
    }

    @Test
    void version_whenFlatWithCeilingLimited_readsShapeAndLimit() {
        // Given
        String relief = """
                "relief": { "type": "FlatWithCeiling", "maximum": "60000", "incomeCeiling": "1200000",
                            "limitedToSlabRateTax": true }""";

        // When
        RuleVersion version = versionWith(relief);

        // Then
        assertThat(version.relief()).contains(new RuleRelief(
                new FlatWithCeiling(Money.of("60000"), Money.of("1200000")), true));
        assertThat(version.cap()).isNull();
    }

    @Test
    void version_whenExcessOverThreshold_readsThreshold() {
        // Given
        String relief = """
                "relief": { "type": "ExcessOverThreshold", "threshold": "1200000",
                            "limitedToSlabRateTax": true }""";

        // When
        RuleVersion version = versionWith(relief);

        // Then
        assertThat(version.relief()).contains(new RuleRelief(
                new ExcessOverThreshold(Money.of("1200000")), true));
    }

    @Test
    void version_whenThresholdLadderCeilingNotLimited_readsThresholdsAndFalse() {
        // Given
        String relief = """
                "relief": { "type": "ThresholdLadderCeiling", "thresholds": ["5000000", "10000000"],
                            "limitedToSlabRateTax": false }""";

        // When
        RuleVersion version = versionWith(relief);

        // Then
        assertThat(version.relief()).contains(new RuleRelief(
                new ThresholdLadderCeiling(List.of(Money.of("5000000"), Money.of("10000000"))), false));
    }

    @Test
    void version_whenNoRelief_readsNoRelief() {
        // Given
        String relief = """
                "relief": { "type": "NoRelief", "limitedToSlabRateTax": false }""";

        // When
        RuleVersion version = versionWith(relief);

        // Then
        assertThat(version.relief()).contains(new RuleRelief(new NoRelief(), false));
    }

    @Test
    void version_whenReliefWithoutConditions_loads() {
        // Given
        String relief = """
                "relief": { "type": "NoRelief", "limitedToSlabRateTax": false }""";

        // When
        RuleVersion version = versionWith(relief);

        // Then
        assertThat(version.conditions()).isEmpty();
    }

    @Test
    void version_whenCapOnly_hasNoRelief() {
        // Given
        String cap = """
                "cap": { "type": "Fixed", "amount": "0" }""";

        // When
        RuleVersion version = versionWith(cap);

        // Then
        assertThat(version.relief()).isEmpty();
    }

    @Test
    void version_whenCapAndRelief_isRefusedNamingThePath() {
        // Given
        String both = """
                "cap": { "type": "Fixed", "amount": "0" },
                "relief": { "type": "NoRelief", "limitedToSlabRateTax": false }""";

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("both a cap and a relief");
    }

    @Test
    void version_whenLimitedToSlabRateTaxAbsent_isRefused() {
        // Given
        String relief = """
                "relief": { "type": "FlatWithCeiling", "maximum": "60000", "incomeCeiling": "1200000" }""";

        // When / Then
        assertThatThrownBy(() -> versionWith(relief))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".relief.limitedToSlabRateTax is required and is absent");
    }

    @Test
    void version_whenReliefTypeUnknown_isRefusedListingTheFour() {
        // Given
        String relief = """
                "relief": { "type": "Rebate", "limitedToSlabRateTax": true }""";

        // When / Then
        assertThatThrownBy(() -> versionWith(relief))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".relief.type must be one of")
                .hasMessageContaining("FlatWithCeiling")
                .hasMessageContaining("ExcessOverThreshold")
                .hasMessageContaining("ThresholdLadderCeiling")
                .hasMessageContaining("NoRelief");
    }

    @Test
    void version_whenNeitherCapReliefNorConditions_isRefused() {
        // Given
        String nothing = """
                "conditions": []""";

        // When / Then
        assertThatThrownBy(() -> versionWith(nothing))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("has no cap, relief or conditions");
    }
}
