package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.Acquisition;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsAmounts;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.SideCharges;
import com.thiru.wealthlens.portfolio.dto.TradeVenue;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.SttEvidence;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Charge;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.ChargeSide;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Each read-model bucket's term and the facts the special-rate provisions turn on, passed to the engine as
 * {@code portfolio} recorded them; which part they make is the rule data's ({@code CapitalGainsNodeTest}; D-289).
 */
@ExtendWith(MockitoExtension.class)
class CapitalGainsAdapterTest {

    private static final String EMAIL = "user@example.com";
    private static final TaxYearId YEAR = TaxYearId.of(2025);
    private static final LocalDate ACQUIRED_ON = LocalDate.of(2023, 4, 3);

    private static final LocalDate TRANSFERRED_ON = LocalDate.of(2025, 6, 10);
    private static final Instant AS_OF = Instant.parse("2026-04-15T10:00:00Z");

    @Mock
    private CapitalGainsSummaryService capitalGainsSummaryService;

    @InjectMocks
    private CapitalGainsAdapter adapter;

    private static CapitalGainsBucket bucket(AssetType assetType, SttEvidence sttOnAcquisition,
            SttEvidence sttOnTransfer, double considerationRupees) {
        return new CapitalGainsBucket(assetType, Optional.empty(), sttOnAcquisition, sttOnTransfer,
                TradeVenue.UNRECORDED, TradeVenue.UNRECORDED,
                Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, amounts(considerationRupees));
    }

    /** Disposals that fetched {@code considerationRupees}, at no cost and no charge. */
    private static CapitalGainsAmounts amounts(double considerationRupees) {
        return new CapitalGainsAmounts(considerationRupees, 0, SideCharges.NONE, SideCharges.NONE);
    }

    /** Long-term equity sold with STT, its buy without, acquired as stated. */
    private static CapitalGainsBucket boughtWithoutStt(Acquisition acquisition, LocalDate acquiredOn) {
        return new CapitalGainsBucket(AssetType.EQUITY, Optional.empty(),
                SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, acquisition, acquiredOn, TRANSFERRED_ON, amounts(1000.50));
    }

    private CapitalGainsInput inputOf(CapitalGainsBucket... buckets) {
        when(capitalGainsSummaryService.summaryFor(anyString(), anyString()))
                .thenReturn(new CapitalGainsSummary(EMAIL, "2025-2026", List.of(buckets), AS_OF, "trade_outcomes:ref"));
        return adapter.inputFor(EMAIL, YEAR);
    }

    private CapitalGainsInput.Bucket onlyBucketOf(CapitalGainsBucket bucket) {
        CapitalGainsInput input = inputOf(bucket);
        assertThat(input.buckets()).hasSize(1);
        return input.buckets().getFirst();
    }

    @Test
    void inputFor_whenTheYearIs2025_asksForPortfoliosFinancialYear() {
        // When
        CapitalGainsInput input = inputOf();

        // Then — portfolio's TLocalDate form; pinned to the summary's run
        verify(capitalGainsSummaryService).summaryFor(EMAIL, "2025-2026");
        assertThat(input.asOf()).isEqualTo(AS_OF);
        assertThat(input.portfolioComputationRef()).isEqualTo("trade_outcomes:ref");
    }

    @Test
    void inputFor_whenEquityWasPurchased_passesItsFactsThrough() {
        // When — STT paid on the buy, nothing on record for the sale, so a read of the wrong side fails
        CapitalGainsInput.Bucket bucket = onlyBucketOf(bucket(AssetType.EQUITY,
                SttEvidence.PAID, SttEvidence.UNKNOWN, 1000.50));

        // Then
        assertThat(bucket.consideration()).isEqualTo(Money.of("1000.50"));
        assertThat(bucket.cost()).isEqualTo(Money.ZERO);
        assertThat(bucket.charges()).isEmpty();
        assertThat(bucket.facts()).isEqualTo(new BucketFacts("EQUITY", "PAID", "UNKNOWN", "PURCHASE", Optional.empty(),
                ACQUIRED_ON, TRANSFERRED_ON, SideVenue.UNRECORDED, SideVenue.UNRECORDED));
    }

    @Test
    void inputFor_whenLongTermEquityWasABonusAllotment_passesTheCorporateActionThrough() {
        // When — STT on the sale only; whether S.O. 5054(E) waives the buy's is the rule data's (W-088f2b)
        CapitalGainsInput.Bucket bucket = onlyBucketOf(boughtWithoutStt(Acquisition.of(CorporateActionType.BONUS),
                LocalDate.of(2020, 1, 10)));

        // Then
        assertThat(bucket.facts()).isEqualTo(new BucketFacts("EQUITY", "NOT_PAID", "PAID", "CORPORATE_ACTION",
                Optional.of("BONUS"), LocalDate.of(2020, 1, 10), TRANSFERRED_ON, SideVenue.UNRECORDED,
                SideVenue.UNRECORDED));
    }

    @Test
    void inputFor_whenTheAcquisitionWasNotRecorded_saysSo() {
        // When — a row written before the acquisition was kept
        CapitalGainsInput.Bucket bucket = onlyBucketOf(boughtWithoutStt(Acquisition.UNRECORDED,
                LocalDate.of(2003, 6, 1)));

        // Then
        assertThat(bucket.facts().acquisitionMode()).isEqualTo("UNRECORDED");
        assertThat(bucket.facts().corporateAction()).isEmpty();
        assertThat(bucket.facts().acquiredOn()).isEqualTo(LocalDate.of(2003, 6, 1));
    }

    @Test
    void inputFor_whenTheAssetIsAFund_passesTheAssetThrough() {
        // When — no row covers a fund unit yet; the engine refuses it, not the adapter
        CapitalGainsInput.Bucket bucket = onlyBucketOf(bucket(AssetType.MUTUAL_FUND, SttEvidence.NOT_PAID,
                SttEvidence.PAID, 10));

        // Then
        assertThat(bucket.facts().assetKind()).isEqualTo("MUTUAL_FUND");
    }

    @Test
    void inputFor_whenTheBucketBoreCharges_passesEachAsItself() {
        // Given — the buy's STT recorded as nil, the sale's misc entered by hand; no misc on the buy
        CapitalGainsBucket charged = new CapitalGainsBucket(AssetType.EQUITY, Optional.empty(), SttEvidence.NOT_PAID,
                SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON,
                TRANSFERRED_ON, new CapitalGainsAmounts(1200, 1000,
                        new SideCharges(Map.of("BROKERAGE", 5.0, "STT", 0.0), 0),
                        new SideCharges(Map.of("BROKERAGE", 7.0), 10)));

        // When
        CapitalGainsInput.Bucket bucket = onlyBucketOf(charged);

        // Then — a code is a fact, so the nil STT passes too; which charges reduce the gain is the engine's
        assertThat(bucket.consideration()).isEqualTo(Money.of("1200"));
        assertThat(bucket.cost()).isEqualTo(Money.of("1000"));
        assertThat(bucket.charges()).containsExactlyInAnyOrder(
                new Charge(ChargeSide.ACQUISITION, "BROKERAGE", Money.of("5")),
                new Charge(ChargeSide.ACQUISITION, "STT", Money.ZERO),
                new Charge(ChargeSide.TRANSFER, "BROKERAGE", Money.of("7")),
                new Charge(ChargeSide.TRANSFER, "MISC", Money.of("10")));
    }

    @Test
    void inputFor_whenTheBucketNetsToNothing_stillPassesIt() {
        // Given — sold for what it cost, with no charge: no gain, but not nothing (W-162a C2a)
        CapitalGainsBucket evens = new CapitalGainsBucket(AssetType.EQUITY, Optional.empty(), SttEvidence.PAID,
                SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, Acquisition.of(null), ACQUIRED_ON,
                TRANSFERRED_ON, new CapitalGainsAmounts(100, 100, SideCharges.NONE, SideCharges.NONE));

        // When
        CapitalGainsInput.Bucket bucket = onlyBucketOf(evens);

        // Then
        assertThat(bucket.consideration()).isEqualTo(Money.of("100"));
        assertThat(bucket.cost()).isEqualTo(Money.of("100"));
    }

    @Test
    void inputFor_whenABucketIsNil_leavesItOut() {
        // When
        CapitalGainsInput input = inputOf(bucket(AssetType.BOND, SttEvidence.NOT_PAID, SttEvidence.NOT_PAID, 0));

        // Then
        assertThat(input.buckets()).isEmpty();
    }

    /**
     * Whatever the asset, the bucket is passed with its facts: whether the head takes a bond, an intraday or derivative
     * sale or a deposit is the engine's rows' to decide, not this class's (W-160a, D-363).
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AssetType.class, names = {"BOND", "EQUITY", "FD"})
    void inputFor_whateverTheAsset_passesTheBucketWithItsFacts(AssetType assetType) {
        // When
        CapitalGainsInput.Bucket passed = onlyBucketOf(bucket(assetType, SttEvidence.NOT_PAID, SttEvidence.NOT_PAID,
                900));

        // Then
        assertThat(passed.facts().assetKind()).isEqualTo(assetType.name());
    }

    @Test
    void inputFor_whenTheBucketRecordsItsVenues_passesThemThrough() {
        // When — bought on BSE for delivery, sold on NSE intraday; the segment as its name (D-298)
        CapitalGainsBucket recorded = new CapitalGainsBucket(AssetType.EQUITY,
                Optional.empty(), SttEvidence.UNKNOWN, SttEvidence.UNKNOWN,
                new TradeVenue(Optional.of("BSE"), Optional.of(TradeSegment.DELIVERY)),
                new TradeVenue(Optional.of("NSE"), Optional.of(TradeSegment.INTRADAY)), Acquisition.of(null),
                ACQUIRED_ON, TRANSFERRED_ON, amounts(10));
        CapitalGainsInput.Bucket bucket = onlyBucketOf(recorded);

        // Then
        assertThat(bucket.facts().acquisitionVenue()).isEqualTo(new SideVenue(Optional.of("BSE"),
                Optional.of("DELIVERY")));
        assertThat(bucket.facts().transferVenue()).isEqualTo(new SideVenue(Optional.of("NSE"),
                Optional.of("INTRADAY")));
    }
}
