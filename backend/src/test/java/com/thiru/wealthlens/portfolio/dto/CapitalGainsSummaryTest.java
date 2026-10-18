package com.thiru.wealthlens.portfolio.dto;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertCanonical;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The published capital-gains read-model holds gross, canonical amounts, one bucket per key (D-237). */
class CapitalGainsSummaryTest {

    private static final Instant AS_OF = Instant.parse("2026-04-15T10:00:00Z");

    private static CapitalGainsBucket equity(CapitalGainsType classification, double gainRupees) {
        return new CapitalGainsBucket(classification, AssetType.EQUITY, Optional.empty(), gainRupees);
    }

    private static CapitalGainsSummary summary(List<CapitalGainsBucket> buckets) {
        return new CapitalGainsSummary("user@example.com", "FY2025-26", buckets, AS_OF, "run-1");
    }

    @Test
    void capitalGainsBucket_whenTheGainCarriesFloatingPointNoise_storesItToThePaisa() {
        // When — TMoney's own example: ₹145.38 as a double sum produces
        CapitalGainsBucket bucket = equity(CapitalGainsType.SHORT_TERM, 145.37999999999997);

        // Then
        assertCanonical(145.38, bucket.gainRupees());
    }

    @Test
    void capitalGainsBucket_whenTheGainIsALoss_keepsItNegative() {
        // When
        CapitalGainsBucket bucket = equity(CapitalGainsType.LONG_TERM, -2500.00);

        // Then — gross, a loss and all; the tax side sets it off
        assertCanonical(-2500.00, bucket.gainRupees());
    }

    @Test
    void capitalGainsBucket_whenTheGainIsNotANumber_isRefused() {
        // When / Then
        assertThatThrownBy(() -> equity(CapitalGainsType.SHORT_TERM, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("divide by zero");
    }

    @Test
    void capitalGainsBucket_whenTheSubClassIsNull_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsBucket(CapitalGainsType.SHORT_TERM, AssetType.EQUITY, null, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void capitalGainsSummary_whenTheSourceListChangesLater_keepsItsOwnBuckets() {
        // Given
        List<CapitalGainsBucket> source = new ArrayList<>(List.of(equity(CapitalGainsType.SHORT_TERM, 100)));
        CapitalGainsSummary summary = summary(source);

        // When
        source.add(equity(CapitalGainsType.LONG_TERM, 200));

        // Then
        assertThat(summary.buckets()).containsExactly(equity(CapitalGainsType.SHORT_TERM, 100));
    }

    @Test
    void capitalGainsSummary_whenTwoBucketsShareAKey_isRefused() {
        // When / Then — one amount split in two
        assertThatThrownBy(() -> summary(List.of(equity(CapitalGainsType.SHORT_TERM, 100),
                equity(CapitalGainsType.SHORT_TERM, 50))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHORT_TERM EQUITY without a sub-class");
    }

    @Test
    void capitalGainsSummary_whenOneClassificationSpansTwoAssets_keepsBoth() {
        // Given — the rate depends on the asset, so equity and a mutual fund stay apart
        CapitalGainsBucket fund = new CapitalGainsBucket(CapitalGainsType.UNCLASSIFIED, AssetType.MUTUAL_FUND,
                Optional.empty(), 400);

        // When
        CapitalGainsSummary summary = summary(List.of(equity(CapitalGainsType.UNCLASSIFIED, 300), fund));

        // Then
        assertThat(summary.buckets()).hasSize(2);
    }

    @Test
    void capitalGainsSummary_whenTheEmailIsBlank_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsSummary(" ", "FY2025-26", List.of(), AS_OF, "run-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs the user");
    }

    @Test
    void capitalGainsSummary_whenTheRunIsNotNamed_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsSummary("user@example.com", "FY2025-26", List.of(), AS_OF, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the run that computed it");
    }

    @Test
    void capitalGainsSummary_whenTheYearOrTheTimeIsMissing_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsSummary("user@example.com", null, List.of(), AS_OF, "run-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapitalGainsSummary("user@example.com", "FY2025-26", List.of(), null, "run-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
