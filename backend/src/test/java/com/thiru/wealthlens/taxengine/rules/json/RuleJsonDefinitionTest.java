package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.definition.TransportDisability;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A rule version stating a definition (D-091, D-092): read like a rounding, at most one of cap, relief, rounding
 * and definition, and a head the engine knows. The values are parse inputs checked for round-trip, not law.
 */
class RuleJsonDefinitionTest {

    private static final String PATH = "law/rules/X.json.versions[0]";

    private static final String REST = """
            "validity": { "type": "Between", "from": "2026-04-01" },
            "regimes": { "type": "AllRegimes" },
            "provision1961": { "instrument": "Income-tax Act, 1961", "reference": "s.17(1)", "presence": "PRESENT" },
            "provision2025": { "instrument": "Income-tax Act, 2025", "reference": "s.16", "presence": "PRESENT" },
            "introducedBy": {
              "instrumentCitation": "Income-tax Act, 2025 (30 of 2025), s.16",
              "gazetteRef": "CG-DL-E-22082025-265620",
              "notifiedOn": "2025-08-21", "effectiveFrom": "2026-04-01",
              "retrospective": false,
              "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
              "verificationLevel": "SOURCE_CHECKED" },
            "legalStatus": "IN_FORCE", "publicationStatus": "PUBLISHED",
            "provenance": {
              "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
              "sourceDescription": "Gazette CG-DL-E-22082025-265620, s.16",
              "accessedOn": "2026-09-26", "verificationLevel": "SOURCE_CHECKED" }""";

    private static final String SALARIES_TOTAL = """
            "definition": { "type": "HeadTotal", "head": "SALARIES" }""";

    private static RuleVersion versionWith(String extra) {
        return RuleJson.version(PackJsonTest.json("{ " + extra + ", " + REST + " }"), PATH);
    }

    @Test
    void version_whenHeadTotal_readsTheHead() {
        // When
        RuleVersion version = versionWith(SALARIES_TOTAL);

        // Then
        assertThat(version.definition()).contains(new HeadTotal(HeadCode.SALARIES));
        assertThat(version.cap()).isNull();
        assertThat(version.relief()).isEmpty();
        assertThat(version.rounding()).isEmpty();
    }

    @Test
    void version_whenSalaryBase_readsIt() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "SalaryBase" }""");

        // Then
        assertThat(version.definition()).contains(new SalaryBase());
        assertThat(version.cap()).isNull();
    }

    @Test
    void version_whenAFlatTransportLimit_readsItsConditionsAndAmount() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "DisabledTransportLimit",
                  "conditions": [ "BLIND", "DEAF_AND_DUMB", "ORTHOPAEDIC_LOWER_EXTREMITIES" ],
                  "perMonth": "3200", "plusDearnessAllowanceThereon": false }""");

        // Then
        assertThat(version.definition()).contains(new DisabledTransportLimit(
                Set.of(TransportDisability.BLIND, TransportDisability.DEAF_AND_DUMB,
                        TransportDisability.ORTHOPAEDIC_LOWER_EXTREMITIES),
                Money.of("3200"), Optional.empty(), false));
    }

    @Test
    void version_whenATransportLimitByCity_readsBothAmounts() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "DisabledTransportLimit",
                  "conditions": [ "BLIND", "DEAF_AND_DUMB", "ORTHOPAEDIC_LOWER_EXTREMITIES",
                                  "ORTHOPAEDIC_UPPER_EXTREMITIES" ],
                  "perMonth": "15000", "perMonthInOtherCities": "8000", "plusDearnessAllowanceThereon": true }""");

        // Then
        assertThat(version.definition()).contains(new DisabledTransportLimit(
                EnumSet.allOf(TransportDisability.class), Money.of("15000"), Optional.of(Money.of("8000")), true));
    }

    @Test
    void version_whenATransportConditionIsUnknown_isRefusedListingTheKnown() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "DisabledTransportLimit", "conditions": [ "LAME" ],
                          "perMonth": "3200", "plusDearnessAllowanceThereon": false }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".definition")
                .hasMessageContaining("BLIND");
    }

    @Test
    void version_whenSalaryBaseBesideACap_isRefusedNamingThePath() {
        // Given
        String both = """
                "cap": { "type": "Fixed", "amount": "0" },
                "definition": { "type": "SalaryBase" }""";

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("a cap")
                .hasMessageContaining("a definition");
    }

    @Test
    void version_whenDefinitionWithoutConditions_loads() {
        // When
        RuleVersion version = versionWith(SALARIES_TOTAL);

        // Then
        assertThat(version.conditions()).isEmpty();
    }

    @Test
    void version_whenCapOnly_hasNoDefinition() {
        // When
        RuleVersion version = versionWith("""
                "cap": { "type": "Fixed", "amount": "0" }""");

        // Then
        assertThat(version.definition()).isEmpty();
    }

    @Test
    void version_whenCapAndDefinition_isRefusedNamingThePath() {
        // Given
        String both = """
                "cap": { "type": "Fixed", "amount": "0" },
                """ + SALARIES_TOTAL;

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("a cap")
                .hasMessageContaining("a definition");
    }

    @Test
    void version_whenRoundingAndDefinition_isRefusedNamingThePath() {
        // Given
        String both = """
                "rounding": { "toNearest": "10" },
                """ + SALARIES_TOTAL;

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("a rounding")
                .hasMessageContaining("a definition");
    }

    @Test
    void version_whenTheDefinitionTypeIsUnknown_isRefusedListingTheKnown() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "HeadAverage", "head": "SALARIES" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".definition")
                .hasMessageContaining("HeadTotal")
                .hasMessageContaining("SalaryBase");
    }

    @Test
    void version_whenTheHeadIsUnknown_isRefusedListingTheKnown() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "HeadTotal", "head": "WAGES" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".definition")
                .hasMessageContaining("SALARIES");
    }
}
