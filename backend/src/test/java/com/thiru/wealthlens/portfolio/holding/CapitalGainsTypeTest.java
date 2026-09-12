package com.thiru.wealthlens.portfolio.holding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CapitalGainsTypeTest {

    @Test
    @DisplayName("the two capital-gains buckets count toward a capital-gains total")
    void countsAsCapitalGains_whenShortOrLongTerm_isTrue() {
        // Given / When / Then
        assertTrue(CapitalGainsType.SHORT_TERM.countsAsCapitalGains());
        assertTrue(CapitalGainsType.LONG_TERM.countsAsCapitalGains());
    }

    @Test
    @DisplayName("speculative income is business income, so it never joins a capital-gains total")
    void countsAsCapitalGains_whenSpeculative_isFalse() {
        // Given / When / Then
        assertFalse(CapitalGainsType.SPECULATIVE.countsAsCapitalGains());
    }

    @Test
    @DisplayName("a different head of income, and an unknown, are both excluded")
    void countsAsCapitalGains_whenNotGainsOrUnclassified_isFalse() {
        // Given / When / Then
        assertFalse(CapitalGainsType.NOT_CAPITAL_GAINS.countsAsCapitalGains());
        assertFalse(CapitalGainsType.UNCLASSIFIED.countsAsCapitalGains());
    }
}
