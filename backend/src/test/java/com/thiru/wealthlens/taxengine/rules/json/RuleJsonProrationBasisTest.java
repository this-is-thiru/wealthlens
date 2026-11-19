package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.pack.MidYearCombination;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.CalendarMonths;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.CompletedMonths;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.Days;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import org.junit.jupiter.api.Test;

/**
 * A rule row names how it measures part of a year when its versions are prorated per segment (D-098). The rule
 * is a parse fixture, not law.
 */
class RuleJsonProrationBasisTest {

    private static final String PATH = "law/rules/X.json";

    private static RuleRow ruleWith(String combination) {
        return RuleJson.rule(PackJsonTest.json("""
                { "ruleId": "FIXTURE-PRORATED",
                  "axis": "TAX_YEAR",
                  %s
                  "versions": [
                    { "validity": { "type": "Between", "from": "2026-04-01" },
                      "regimes": { "type": "AllRegimes" },
                      "provision1961": { "instrument": "Income-tax Act, 1961", "reference": "s.16", "presence": "PRESENT" },
                      "provision2025": { "instrument": "Income-tax Act, 2025", "reference": "s.19", "presence": "PRESENT" },
                      "cap": { "type": "Fixed", "amount": "0" },
                      "conditions": [],
                      "introducedBy": {
                        "instrumentCitation": "Income-tax Act, 2025 (30 of 2025), s.19",
                        "gazetteRef": "CG-DL-E-22082025-265620",
                        "notifiedOn": "2025-08-21", "effectiveFrom": "2026-04-01",
                        "retrospective": false,
                        "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                        "verificationLevel": "SOURCE_CHECKED" },
                      "legalStatus": "IN_FORCE", "publicationStatus": "PUBLISHED",
                      "provenance": {
                        "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                        "sourceDescription": "fixture", "accessedOn": "2026-09-27",
                        "verificationLevel": "SOURCE_CHECKED" } } ],
                  "headProvenance": {
                    "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                    "sourceDescription": "fixture", "accessedOn": "2026-09-27",
                    "verificationLevel": "SOURCE_CHECKED" } }""".formatted(combination)), PATH);
    }

    @Test
    void rule_whenProratedByDays_readsTheBasis() {
        // When
        RuleRow row = ruleWith("""
                "midYearCombination": "PRORATE_PER_SEGMENT", "prorationBasis": "DAYS",""");

        // Then
        assertThat(row.midYearCombination()).isEqualTo(MidYearCombination.PRORATE_PER_SEGMENT);
        assertThat(row.prorationBasis()).contains(new Days());
    }

    @Test
    void rule_whenProratedByMonths_readsEachMonthBasis() {
        assertThat(ruleWith("""
                        "midYearCombination": "PRORATE_PER_SEGMENT", "prorationBasis": "CALENDAR_MONTHS",""")
                        .prorationBasis())
                .contains(new CalendarMonths());
        assertThat(ruleWith("""
                        "midYearCombination": "PRORATE_PER_SEGMENT", "prorationBasis": "COMPLETED_MONTHS",""")
                        .prorationBasis())
                .contains(new CompletedMonths());
    }

    @Test
    void rule_whenNotProrated_hasNoBasis() {
        assertThat(ruleWith("""
                        "midYearCombination": "LATEST_VERSION_GOVERNS",""").prorationBasis())
                .isEmpty();
    }

    @Test
    void rule_whenTheBasisIsUnknown_isRefusedListingTheKnown() {
        assertThatThrownBy(() -> ruleWith("""
                        "midYearCombination": "PRORATE_PER_SEGMENT", "prorationBasis": "WEEKS","""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("prorationBasis")
                .hasMessageContaining("DAYS");
    }

    @Test
    void rule_whenProratedWithoutABasis_isRefusedNamingIt() {
        assertThatThrownBy(() -> ruleWith("""
                        "midYearCombination": "PRORATE_PER_SEGMENT","""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("prorationBasis");
    }
}
