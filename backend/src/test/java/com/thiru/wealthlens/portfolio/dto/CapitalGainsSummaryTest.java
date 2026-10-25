package com.thiru.wealthlens.portfolio.dto;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertCanonical;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.SttEvidence;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The published capital-gains read-model holds gross, canonical amounts, one bucket per key (D-237). */
class CapitalGainsSummaryTest {

    private static final LocalDate ACQUIRED_ON = LocalDate.of(2023, 4, 3);

    private static final LocalDate TRANSFERRED_ON = LocalDate.of(2025, 6, 10);
    private static final Instant AS_OF = Instant.parse("2026-04-15T10:00:00Z");

    private static CapitalGainsBucket equity(double gainRupees) {
        return new CapitalGainsBucket(AssetType.EQUITY, Optional.empty(), SttEvidence.PAID, SttEvidence.PAID,
                TradeVenue.UNRECORDED, TradeVenue.UNRECORDED,
                Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, gainRupees);
    }

    private static CapitalGainsSummary summary(List<CapitalGainsBucket> buckets) {
        return new CapitalGainsSummary("user@example.com", "FY2025-26", buckets, AS_OF, "run-1");
    }

    @Test
    void capitalGainsBucket_whenTheGainCarriesFloatingPointNoise_storesItToThePaisa() {
        // When — TMoney's own example: ₹145.38 as a double sum produces
        CapitalGainsBucket bucket = equity(145.37999999999997);

        // Then
        assertCanonical(145.38, bucket.gainRupees());
    }

    @Test
    void capitalGainsBucket_whenTheGainIsALoss_keepsItNegative() {
        // When
        CapitalGainsBucket bucket = equity(-2500.00);

        // Then — gross, a loss and all; the tax side sets it off
        assertCanonical(-2500.00, bucket.gainRupees());
    }

    @Test
    void capitalGainsBucket_whenTheGainIsNotANumber_isRefused() {
        // When / Then
        assertThatThrownBy(() -> equity(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("divide by zero");
    }

    @Test
    void capitalGainsBucket_whenTheSubClassIsNull_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsBucket(AssetType.EQUITY, null, SttEvidence.PAID,
                SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void capitalGainsSummary_whenTheSourceListChangesLater_keepsItsOwnBuckets() {
        // Given
        List<CapitalGainsBucket> source = new ArrayList<>(List.of(equity(100)));
        CapitalGainsSummary summary = summary(source);

        // When
        source.add(equity(200));

        // Then
        assertThat(summary.buckets()).containsExactly(equity(100));
    }

    @Test
    void capitalGainsSummary_whenTwoBucketsShareAKey_isRefused() {
        // When / Then — one amount split in two
        assertThatThrownBy(() -> summary(List.of(equity(100),
                equity(50))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EQUITY without a sub-class with the same STT");
    }

    @Test
    void capitalGainsSummary_whenTwoAssetsShareTheirOtherFacts_keepsBoth() {
        // Given — the rate depends on the asset, so equity and a mutual fund stay apart
        CapitalGainsBucket fund = new CapitalGainsBucket(AssetType.MUTUAL_FUND,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.NOT_PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, 400);

        // When
        CapitalGainsSummary summary = summary(List.of(equity(300), fund));

        // Then
        assertThat(summary.buckets()).hasSize(2);
    }

    @Test
    void capitalGainsSummary_whenTwoBucketsDifferOnlyInStt_keepsBoth() {
        // Given — the special rates turn on STT (S2 ss.196, 198), so a sale with and one without stay apart (D-241)
        CapitalGainsBucket withoutStt = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.PAID, SttEvidence.NOT_PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, 50);

        // When
        CapitalGainsSummary summary = summary(List.of(equity(100), withoutStt));

        // Then
        assertThat(summary.buckets()).hasSize(2);
    }

    @Test
    void capitalGainsSummary_whenTwoBucketsDifferOnlyInHowOrWhenAcquired_keepsThemApart() {
        // Given — the facts a waiver of the STT condition turns on (S.O. 5054(E)), so none is summed away
        CapitalGainsBucket bonus = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(CorporateActionType.BONUS), ACQUIRED_ON, TRANSFERRED_ON, 50);
        CapitalGainsBucket purchase = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, 60);
        CapitalGainsBucket purchaseLater = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON.plusDays(1), TRANSFERRED_ON, 70);

        // When
        CapitalGainsSummary summary = summary(List.of(bonus, purchase, purchaseLater));

        // Then
        assertThat(summary.buckets()).hasSize(3);
    }

    @Test
    void capitalGainsSummary_whenTwoBucketsDifferOnlyInWhenSold_keepsThemApart() {
        // Given -- W-088k2 case 4: the holding period runs to each sale date (D-304)
        CapitalGainsBucket sold = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED,
                Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, 50);
        CapitalGainsBucket soldNextDay = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED,
                Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON.plusDays(1), 60);

        // When
        CapitalGainsSummary summary = summary(List.of(sold, soldNextDay));

        // Then
        assertThat(summary.buckets()).hasSize(2);
    }

    @Test
    void capitalGainsBucket_whenTheTransferDateIsNull_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED,
                Acquisition.of(null), ACQUIRED_ON, null, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transfer date");
    }

    @Test
    void capitalGainsBucket_whenTheAcquisitionDateIsNull_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), null, TRANSFERRED_ON, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acquisition date");
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
