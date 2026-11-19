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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The published capital-gains read-model holds gross, canonical amounts, one bucket per key (D-237). */
class CapitalGainsSummaryTest {

    private static final LocalDate ACQUIRED_ON = LocalDate.of(2023, 4, 3);

    private static final LocalDate TRANSFERRED_ON = LocalDate.of(2025, 6, 10);
    private static final Instant AS_OF = Instant.parse("2026-04-15T10:00:00Z");

    /** Disposals that fetched {@code considerationRupees}, at no cost and no charge. */
    private static CapitalGainsAmounts amounts(double considerationRupees) {
        return new CapitalGainsAmounts(considerationRupees, 0, SideCharges.NONE, SideCharges.NONE);
    }

    private static CapitalGainsBucket equity(CapitalGainsAmounts amounts) {
        return new CapitalGainsBucket(AssetType.EQUITY, Optional.empty(), SttEvidence.PAID, SttEvidence.PAID,
                TradeVenue.UNRECORDED, TradeVenue.UNRECORDED,
                Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, amounts);
    }

    private static CapitalGainsSummary summary(List<CapitalGainsBucket> buckets) {
        return new CapitalGainsSummary("user@example.com", "FY2025-26", buckets, AS_OF, "run-1");
    }

    @Test
    void capitalGainsAmounts_whenAnAmountCarriesFloatingPointNoise_storesItToThePaisa() {
        // When — TMoney's own example: ₹145.38 as a double sum produces 145.37999999999997
        CapitalGainsAmounts amounts = new CapitalGainsAmounts(145.37999999999997, 145.37999999999997,
                new SideCharges(Map.of("BROKERAGE", 145.37999999999997), 145.37999999999997), SideCharges.NONE);

        // Then
        assertCanonical(145.38, amounts.considerationRupees());
        assertCanonical(145.38, amounts.costRupees());
        assertCanonical(145.38, amounts.acquisition().rupeesByCode().get("BROKERAGE"));
        assertCanonical(145.38, amounts.acquisition().miscRupees());
    }

    @Test
    void capitalGainsAmounts_whenTheCostExceedsTheConsideration_keepsBoth() {
        // When — sold for 1,000 what cost 3,500
        CapitalGainsBucket bucket = equity(new CapitalGainsAmounts(1000, 3500, SideCharges.NONE, SideCharges.NONE));

        // Then — no gain is netted here, so the loss of 2,500 is the tax side's to compute and set off
        assertCanonical(1000.00, bucket.amounts().considerationRupees());
        assertCanonical(3500.00, bucket.amounts().costRupees());
    }

    @Test
    void capitalGainsAmounts_whenAnAmountIsNotANumber_isRefused() {
        // When / Then
        assertThatThrownBy(() -> amounts(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("divide by zero");
    }

    @Test
    void capitalGainsAmounts_plus_whenBothSidesBoreCharges_sumsThemCodeByCode() {
        // Given
        CapitalGainsAmounts a = new CapitalGainsAmounts(1200, 1000, new SideCharges(Map.of("BROKERAGE", 5.0), 0),
                new SideCharges(Map.of("BROKERAGE", 7.0, "STT", 1.20), 0));
        CapitalGainsAmounts b = new CapitalGainsAmounts(600, 520, new SideCharges(Map.of("BROKERAGE", 2.0), 0),
                new SideCharges(Map.of("BROKERAGE", 3.0, "STT", 0.60), 10));

        // When
        CapitalGainsAmounts sum = a.plus(b);

        // Then — 1,200 + 600 = 1,800; 1,000 + 520 = 1,520; buy brokerage 5 + 2 = 7; sale brokerage 7 + 3 = 10,
        // STT 1.20 + 0.60 = 1.80; misc 0 + 10 = 10
        assertCanonical(1800.00, sum.considerationRupees());
        assertCanonical(1520.00, sum.costRupees());
        assertCanonical(7.00, sum.acquisition().rupeesByCode().get("BROKERAGE"));
        assertCanonical(10.00, sum.transfer().rupeesByCode().get("BROKERAGE"));
        assertCanonical(1.80, sum.transfer().rupeesByCode().get("STT"));
        assertCanonical(10.00, sum.transfer().miscRupees());
    }

    @Test
    void capitalGainsBucket_whenTheAmountsAreNull_isRefused() {
        // When / Then
        assertThatThrownBy(() -> equity(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amounts");
    }

    @Test
    void capitalGainsBucket_whenTheSubClassIsNull_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsBucket(AssetType.EQUITY, null, SttEvidence.PAID,
                SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, amounts(100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void capitalGainsSummary_whenTheSourceListChangesLater_keepsItsOwnBuckets() {
        // Given
        List<CapitalGainsBucket> source = new ArrayList<>(List.of(equity(amounts(100))));
        CapitalGainsSummary summary = summary(source);

        // When
        source.add(equity(amounts(200)));

        // Then
        assertThat(summary.buckets()).containsExactly(equity(amounts(100)));
    }

    @Test
    void capitalGainsSummary_whenTwoBucketsShareAKey_isRefused() {
        // When / Then — one amount split in two
        assertThatThrownBy(() -> summary(List.of(equity(amounts(100)),
                equity(amounts(50)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EQUITY without a sub-class with the same STT");
    }

    @Test
    void capitalGainsSummary_whenTwoAssetsShareTheirOtherFacts_keepsBoth() {
        // Given — the rate depends on the asset, so equity and a mutual fund stay apart
        CapitalGainsBucket fund = new CapitalGainsBucket(AssetType.MUTUAL_FUND,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.NOT_PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, amounts(400));

        // When
        CapitalGainsSummary summary = summary(List.of(equity(amounts(300)), fund));

        // Then
        assertThat(summary.buckets()).hasSize(2);
    }

    @Test
    void capitalGainsSummary_whenTwoBucketsDifferOnlyInStt_keepsBoth() {
        // Given — the special rates turn on STT (S2 ss.196, 198), so a sale with and one without stay apart (D-241)
        CapitalGainsBucket withoutStt = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.PAID, SttEvidence.NOT_PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, amounts(50));

        // When
        CapitalGainsSummary summary = summary(List.of(equity(amounts(100)), withoutStt));

        // Then
        assertThat(summary.buckets()).hasSize(2);
    }

    @Test
    void capitalGainsSummary_whenTwoBucketsDifferOnlyInHowOrWhenAcquired_keepsThemApart() {
        // Given — the facts a waiver of the STT condition turns on (S.O. 5054(E)), so none is summed away
        CapitalGainsBucket bonus = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(CorporateActionType.BONUS), ACQUIRED_ON, TRANSFERRED_ON, amounts(50));
        CapitalGainsBucket purchase = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, amounts(60));
        CapitalGainsBucket purchaseLater = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON.plusDays(1), TRANSFERRED_ON, amounts(70));

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
                Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, amounts(50));
        CapitalGainsBucket soldNextDay = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED,
                Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON.plusDays(1), amounts(60));

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
                Acquisition.of(null), ACQUIRED_ON, null, amounts(100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transfer date");
    }

    @Test
    void capitalGainsBucket_whenTheAcquisitionDateIsNull_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), null, TRANSFERRED_ON, amounts(100)))
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
