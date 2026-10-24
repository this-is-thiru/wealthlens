package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.Acquisition;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.TradeVenue;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.SttEvidence;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    private static CapitalGainsBucket bucket(CapitalGainsType classification, AssetType assetType,
            SttEvidence sttOnAcquisition, SttEvidence sttOnTransfer, double gainRupees) {
        return new CapitalGainsBucket(classification, assetType, Optional.empty(), sttOnAcquisition, sttOnTransfer,
                TradeVenue.UNRECORDED, TradeVenue.UNRECORDED,
                Acquisition.of(null), ACQUIRED_ON, TRANSFERRED_ON, gainRupees);
    }

    /** Long-term equity sold with STT, its buy without, acquired as stated. */
    private static CapitalGainsBucket boughtWithoutStt(Acquisition acquisition, LocalDate acquiredOn) {
        return new CapitalGainsBucket(CapitalGainsType.LONG_TERM, AssetType.EQUITY, Optional.empty(),
                SttEvidence.NOT_PAID, SttEvidence.PAID, TradeVenue.UNRECORDED, TradeVenue.UNRECORDED, acquisition, acquiredOn, TRANSFERRED_ON, 1000.50);
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
        CapitalGainsInput.Bucket bucket = onlyBucketOf(bucket(CapitalGainsType.SHORT_TERM, AssetType.EQUITY,
                SttEvidence.PAID, SttEvidence.UNKNOWN, 1000.50));

        // Then
        assertThat(bucket.gain()).isEqualTo(Money.of("1000.50"));
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
    void inputFor_whenPortfolioClassifiesTheSameFactsShortOrLong_passesTheSameBucket() {
        // When — the engine decides the term from the dates (D-305); portfolio's classification only says the
        // disposal is a capital gain, so it cannot reach the engine
        CapitalGainsInput.Bucket asShort = onlyBucketOf(bucket(CapitalGainsType.SHORT_TERM, AssetType.EQUITY,
                SttEvidence.PAID, SttEvidence.PAID, 100));
        CapitalGainsInput.Bucket asLong = onlyBucketOf(bucket(CapitalGainsType.LONG_TERM, AssetType.EQUITY,
                SttEvidence.PAID, SttEvidence.PAID, 100));

        // Then
        assertThat(asShort).isEqualTo(asLong);
        assertThat(asShort.facts().transferredOn()).isEqualTo(TRANSFERRED_ON);
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
    void inputFor_whenAFundHasACapitalGainsClassification_passesTheAssetThrough() {
        // When — no row covers a fund unit yet; the engine refuses it, not the adapter
        CapitalGainsInput.Bucket bucket = onlyBucketOf(bucket(CapitalGainsType.SHORT_TERM, AssetType.MUTUAL_FUND,
                SttEvidence.NOT_PAID, SttEvidence.PAID, 10));

        // Then
        assertThat(bucket.facts().assetKind()).isEqualTo("MUTUAL_FUND");
    }

    @Test
    void inputFor_whenAnUnclassifiedBucketIsNil_leavesItOut() {
        // When
        CapitalGainsInput input = inputOf(bucket(CapitalGainsType.UNCLASSIFIED, AssetType.BOND, SttEvidence.NOT_PAID, SttEvidence.NOT_PAID, 0));

        // Then
        assertThat(input.buckets()).isEmpty();
    }

    /**
     * The classification is not read: an intraday, derivative or deposit bucket is passed with its facts, and the
     * engine's RS-CG-NOT-COMPUTED and RS-CG-ELSEWHERE rows decide whether the head takes it (W-160a, D-363).
     */
    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("everyClassification")
    void inputFor_whateverPortfoliosClassification_passesTheBucketWithItsFacts(CapitalGainsType classification,
            AssetType assetType) {
        // When
        CapitalGainsInput.Bucket passed = onlyBucketOf(bucket(classification, assetType, SttEvidence.NOT_PAID,
                SttEvidence.NOT_PAID, 900));

        // Then
        assertThat(passed.facts().assetKind()).isEqualTo(assetType.name());
    }

    private static Stream<Arguments> everyClassification() {
        return Stream.of(
                Arguments.of(CapitalGainsType.UNCLASSIFIED, AssetType.BOND),
                Arguments.of(CapitalGainsType.SPECULATIVE, AssetType.EQUITY),
                Arguments.of(CapitalGainsType.NOT_CAPITAL_GAINS, AssetType.FD),
                Arguments.of(CapitalGainsType.SHORT_TERM, AssetType.EQUITY));
    }

    @Test
    void inputFor_whenTheBucketRecordsItsVenues_passesThemThrough() {
        // When — bought on BSE for delivery, sold on NSE intraday; the segment as its name (D-298)
        CapitalGainsBucket recorded = new CapitalGainsBucket(CapitalGainsType.SHORT_TERM, AssetType.EQUITY,
                Optional.empty(), SttEvidence.UNKNOWN, SttEvidence.UNKNOWN,
                new TradeVenue(Optional.of("BSE"), Optional.of(TradeSegment.DELIVERY)),
                new TradeVenue(Optional.of("NSE"), Optional.of(TradeSegment.INTRADAY)), Acquisition.of(null),
                ACQUIRED_ON, TRANSFERRED_ON, 10);
        CapitalGainsInput.Bucket bucket = onlyBucketOf(recorded);

        // Then
        assertThat(bucket.facts().acquisitionVenue()).isEqualTo(new SideVenue(Optional.of("BSE"),
                Optional.of("DELIVERY")));
        assertThat(bucket.facts().transferVenue()).isEqualTo(new SideVenue(Optional.of("NSE"),
                Optional.of("INTRADAY")));
    }
}
