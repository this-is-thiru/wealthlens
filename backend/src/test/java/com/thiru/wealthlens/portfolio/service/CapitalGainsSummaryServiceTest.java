package com.thiru.wealthlens.portfolio.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertCanonical;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.Acquisition;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.TradeVenue;
import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.SttEvidence;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The published capital-gains read-model, from hand-worked rows (D-238): each bucket's sale value, adjusted cost and
 * charges by code, summed over its rows; the gain is the engine's to net (W-162a).
 */
@ExtendWith(MockitoExtension.class)
class CapitalGainsSummaryServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String YEAR = "2025-2026";
    private static final LocalDate A_AND_B_BOUGHT_ON = LocalDate.of(2025, 1, 2);

    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;

    @InjectMocks
    private CapitalGainsSummaryService service;

    /** A disposal, with no charges until {@link #charged} gives it some. */
    private static TradeOutcomeEntity row(String id, AssetType assetType, double sellPrice, double buyPrice,
            double quantity) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setStockCode("STOCK-" + id);
        row.setAssetType(assetType);
        row.setSellDate(LocalDate.of(2025, 6, 2));
        row.setBuyDate(LocalDate.of(2024, 1, 2));
        row.setSellPrice(sellPrice);
        row.setSellQuantity(quantity);
        row.setCorporateActionAdjustedBuyPrice(buyPrice);
        row.setBuyQuantity(quantity);
        return row;
    }

    /** The row with brokerage on each side, broken down by code. */
    private static TradeOutcomeEntity charged(TradeOutcomeEntity row, double buyBrokerage, double sellBrokerage) {
        row.setBuyBrokerCharges(buyBrokerage);
        row.setBuyChargeBreakup(Map.of("BROKERAGE", buyBrokerage));
        row.setSellBrokerCharges(sellBrokerage);
        row.setSellChargeBreakup(Map.of("BROKERAGE", sellBrokerage));
        return row;
    }

    /** The row's sale bore STT and its buy none, the case S.O. 5054(E) decides. */
    private static TradeOutcomeEntity soldWithStt(TradeOutcomeEntity row) {
        row.setSellBrokerCharges(0.10);
        row.setSellChargeBreakup(Map.of("STT", 0.10));
        return row;
    }

    /** Rows A, B (equity bought 2025-01-02) and C (equity bought 2024-01-02) of the plan's table, each sale bearing STT. */
    private static List<TradeOutcomeEntity> rowsAtoC() {
        TradeOutcomeEntity a = charged(row("A", AssetType.EQUITY, 120, 100, 10), 5, 7);
        a.setSellChargeBreakup(Map.of("BROKERAGE", 7.0, "STT", 1.20));
        a.setSellBrokerCharges(8.20);
        a.setNetProfit(178.80);
        a.setBuyDate(A_AND_B_BOUGHT_ON);
        TradeOutcomeEntity b = charged(row("B", AssetType.EQUITY, 150, 130, 4), 2, 3);
        b.setSellMiscCharges(10);
        b.setSellChargeBreakup(Map.of("BROKERAGE", 3.0, "STT", 0.60));
        b.setBuyDate(A_AND_B_BOUGHT_ON);
        TradeOutcomeEntity c = charged(row("C", AssetType.EQUITY, 500, 300, 2), 4, 6);
        c.setSellChargeBreakup(Map.of("BROKERAGE", 6.0, "STT", 1.00));
        return List.of(a, b, c);
    }

    private CapitalGainsSummary summaryOf(List<TradeOutcomeEntity> rows) {
        when(tradeOutcomeRepository.findByEmailAndSellDateBetween(eq(EMAIL), any(), any())).thenReturn(rows);
        return service.summaryFor(EMAIL, YEAR);
    }

    private static CapitalGainsBucket bucket(CapitalGainsSummary summary, LocalDate acquiredOn) {
        return summary.buckets().stream().filter(b -> b.acquiredOn().equals(acquiredOn)).findFirst().orElseThrow();
    }

    /** The bucket's sale value and cost. */
    private static void assertFetchedAndCost(CapitalGainsBucket bucket, double considerationRupees, double costRupees) {
        assertCanonical(considerationRupees, bucket.amounts().considerationRupees());
        assertCanonical(costRupees, bucket.amounts().costRupees());
    }

    @Test
    void summaryFor_whenRowsShareTheirFacts_sumsTheirAmounts() {
        // When
        CapitalGainsSummary summary = summaryOf(rowsAtoC());

        // Then — A + B: 1,200 + 600 = 1,800 fetched, 1,000 + 520 = 1,520 cost; buy brokerage 5 + 2 = 7; sale brokerage
        // 7 + 3 = 10, STT 1.20 + 0.60 = 1.80, misc 10 (B's). C: 1,000 fetched, 600 cost; brokerage 4 and 6, STT 1.00
        assertThat(summary.buckets()).hasSize(2);
        CapitalGainsBucket ab = bucket(summary, A_AND_B_BOUGHT_ON);
        assertFetchedAndCost(ab, 1800.00, 1520.00);
        assertCanonical(7.00, ab.amounts().acquisition().rupeesByCode().get("BROKERAGE"));
        assertCanonical(10.00, ab.amounts().transfer().rupeesByCode().get("BROKERAGE"));
        assertCanonical(1.80, ab.amounts().transfer().rupeesByCode().get("STT"));
        assertCanonical(10.00, ab.amounts().transfer().miscRupees());
        CapitalGainsBucket c = bucket(summary, LocalDate.of(2024, 1, 2));
        assertFetchedAndCost(c, 1000.00, 600.00);
        assertCanonical(4.00, c.amounts().acquisition().rupeesByCode().get("BROKERAGE"));
        assertCanonical(6.00, c.amounts().transfer().rupeesByCode().get("BROKERAGE"));
        assertCanonical(1.00, c.amounts().transfer().rupeesByCode().get("STT"));
        assertThat(ab.assetType()).isEqualTo(AssetType.EQUITY);
    }

    @Test
    void summaryFor_whenARowPaidStt_publishesItAsACharge() {
        // When — row A's STT 1.20 is in its breakdown; whether it reduces the gain is the engine's (W-162a)
        CapitalGainsSummary summary = summaryOf(List.of(rowsAtoC().getFirst()));

        // Then — published as recorded, beside the brokerage, and not netted against anything
        CapitalGainsBucket a = summary.buckets().getFirst();
        assertFetchedAndCost(a, 1200.00, 1000.00);
        assertThat(a.amounts().transfer().rupeesByCode()).containsOnlyKeys("BROKERAGE", "STT");
        assertCanonical(1.20, a.amounts().transfer().rupeesByCode().get("STT"));
    }

    @Test
    void summaryFor_whenRowsCarrySubPaisaAmounts_takesEachRowToThePaisaBeforeSumming() {
        // Given — two sales of a fund at NAV 12.3456, bought at 10.1111, 10.123 units each (W-162a C1a)
        TradeOutcomeEntity first = row("P", AssetType.MUTUAL_FUND, 12.3456, 10.1111, 10.123);
        TradeOutcomeEntity second = row("Q", AssetType.MUTUAL_FUND, 12.3456, 10.1111, 10.123);

        // When
        CapitalGainsBucket fund = summaryOf(List.of(first, second)).buckets().getFirst();

        // Then — 12.3456 × 10.123 = 124.9745088 → 124.97 per row, so 249.94 (summed first it would be 249.95);
        // 10.1111 × 10.123 = 102.3546653 → 102.35 per row, so 204.70 (summed first, 204.71)
        assertFetchedAndCost(fund, 249.94, 204.70);
    }

    @Test
    void summaryFor_whenTheBreakdownsShowStt_recordsItOnEachSide() {
        // Given — row A's sale bore STT 1.20, its buy only brokerage; row D's buy and sale both bore STT
        TradeOutcomeEntity d = charged(row("D", AssetType.EQUITY, 110, 100, 1), 0, 0);
        d.setBuyChargeBreakup(Map.of("STT", 0.10));
        d.setBuyBrokerCharges(0.10);
        d.setSellChargeBreakup(Map.of("STT", 0.11));
        d.setSellBrokerCharges(0.11);

        // When
        CapitalGainsSummary summary = summaryOf(List.of(rowsAtoC().getFirst(), d));

        // Then — two short-term equity buckets, apart by STT on the buy (D-241); A's buy breakdown holds only
        // brokerage, so it records none (D-292)
        assertThat(summary.buckets()).hasSize(2);
        assertThat(summary.buckets().get(0).sttOnAcquisition()).isEqualTo(SttEvidence.NOT_PAID);
        assertThat(summary.buckets().get(0).sttOnTransfer()).isEqualTo(SttEvidence.PAID);
        assertThat(summary.buckets().get(1).sttOnAcquisition()).isEqualTo(SttEvidence.PAID);
        assertThat(summary.buckets().get(1).sttOnTransfer()).isEqualTo(SttEvidence.PAID);
    }

    @Test
    void summaryFor_whenNeitherSideHasABreakdown_recordsSttAsUnknownOnBoth() {
        // Given — a V1 sell with nil charges: nothing on record either side, which F1 read as no STT
        TradeOutcomeEntity v1 = row("V", AssetType.EQUITY, 120, 100, 1);

        // When
        CapitalGainsBucket only = summaryOf(List.of(v1)).buckets().getFirst();

        // Then — unknown, never NOT_PAID (D-292)
        assertThat(only.sttOnAcquisition()).isEqualTo(SttEvidence.UNKNOWN);
        assertThat(only.sttOnTransfer()).isEqualTo(SttEvidence.UNKNOWN);
    }

    @Test
    void summaryFor_whenOnlyTheBuyHasNoBreakdown_recordsEachSideAsItStands() {
        // Given — the buy predates the charge engine; the sale's breakdown levied STT
        TradeOutcomeEntity row = soldWithStt(row("W", AssetType.EQUITY, 120, 100, 1));

        // When
        CapitalGainsBucket only = summaryOf(List.of(row)).buckets().getFirst();

        // Then
        assertThat(only.sttOnAcquisition()).isEqualTo(SttEvidence.UNKNOWN);
        assertThat(only.sttOnTransfer()).isEqualTo(SttEvidence.PAID);
    }

    @Test
    void summaryFor_whenRowsDifferOnlyInWhetherTheBuysSttIsKnown_publishesThemApart() {
        // Given — one key but for the buy's evidence: PAID on X, nothing on record on Y
        TradeOutcomeEntity known = soldWithStt(row("X", AssetType.EQUITY, 110, 100, 1));
        known.setBuyChargeBreakup(Map.of("STT", 0.10));
        known.setBuyBrokerCharges(0.10);
        TradeOutcomeEntity unknown = soldWithStt(row("Y", AssetType.EQUITY, 130, 100, 1));

        // When
        CapitalGainsSummary summary = summaryOf(List.of(known, unknown));

        // Then — two buckets: 110 fetched for 100, and 130 for 100, by hand
        assertThat(summary.buckets()).extracting(CapitalGainsBucket::sttOnAcquisition)
                .containsExactly(SttEvidence.PAID, SttEvidence.UNKNOWN);
        assertFetchedAndCost(summary.buckets().get(0), 110.00, 100.00);
        assertFetchedAndCost(summary.buckets().get(1), 130.00, 100.00);
    }

    @Test
    void summaryFor_whenRowsDifferInHowOrWhenAcquired_publishesTheFactsApart() {
        // Given -- long-term equity sold with STT, no STT on any buy: a bonus, a row from before the acquisition was
        // kept, and two purchases a day apart. Facts only: no date means anything here (D-249)
        TradeOutcomeEntity bonus = soldWithStt(row("E", AssetType.EQUITY, 110, 0, 1));
        bonus.setAcquisitionMode(AcquisitionMode.CORPORATE_ACTION);
        bonus.setAcquisitionCorporateAction(CorporateActionType.BONUS);
        TradeOutcomeEntity unrecorded = soldWithStt(row("F", AssetType.EQUITY, 120, 100, 1));
        TradeOutcomeEntity first = soldWithStt(row("G", AssetType.EQUITY, 130, 100, 1));
        first.setAcquisitionMode(AcquisitionMode.PURCHASE);
        first.setBuyDate(LocalDate.of(2004, 9, 30));
        TradeOutcomeEntity next = soldWithStt(row("H", AssetType.EQUITY, 140, 100, 1));
        next.setAcquisitionMode(AcquisitionMode.PURCHASE);
        next.setBuyDate(LocalDate.of(2004, 10, 1));

        // When
        CapitalGainsSummary summary = summaryOf(List.of(bonus, unrecorded, first, next));

        // Then -- four buckets; 110 for 0, 120, 130 and 140 for 100 each, by hand
        assertThat(summary.buckets()).extracting(CapitalGainsBucket::acquisition, CapitalGainsBucket::acquiredOn)
                .containsExactly(tuple(Acquisition.of(CorporateActionType.BONUS), LocalDate.of(2024, 1, 2)),
                        tuple(Acquisition.UNRECORDED, LocalDate.of(2024, 1, 2)),
                        tuple(Acquisition.of(null), LocalDate.of(2004, 9, 30)),
                        tuple(Acquisition.of(null), LocalDate.of(2004, 10, 1)));
        assertFetchedAndCost(summary.buckets().get(0), 110.00, 0.00);
        assertFetchedAndCost(summary.buckets().get(3), 140.00, 100.00);
    }

    @Test
    void summaryFor_whenARowHasNoBuyDate_isRefused() {
        // Given
        TradeOutcomeEntity undated = row("J", AssetType.EQUITY, 120, 100, 1);
        undated.setBuyDate(null);

        // When / Then
        assertThatThrownBy(() -> summaryOf(List.of(undated)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no buy date");
    }

    @Test
    void summaryFor_whenRowsDifferOnlyInWhenSold_publishesEachSaleDateApart() {
        // Given -- W-088k2 cases 1, 2: two sales of one lot on 2-6-2025 and one on 3-6-2025; the engine counts the
        // holding period to each date (D-304)
        TradeOutcomeEntity first = row("K", AssetType.EQUITY, 120, 100, 1);
        TradeOutcomeEntity sameDay = row("L", AssetType.EQUITY, 130, 100, 1);
        TradeOutcomeEntity nextDay = row("M", AssetType.EQUITY, 140, 100, 1);
        nextDay.setSellDate(LocalDate.of(2025, 6, 3));

        // When
        CapitalGainsSummary summary = summaryOf(List.of(first, sameDay, nextDay));

        // Then -- 120 + 130 = 250 for 200 on 2-6-2025, 140 for 100 on 3-6-2025, by hand
        assertThat(summary.buckets()).extracting(CapitalGainsBucket::transferredOn)
                .containsExactly(LocalDate.of(2025, 6, 2), LocalDate.of(2025, 6, 3));
        assertFetchedAndCost(summary.buckets().get(0), 250.00, 200.00);
        assertFetchedAndCost(summary.buckets().get(1), 140.00, 100.00);
    }

    @Test
    void summaryFor_whenARowHasNoSellDate_isRefusedNamingIt() {
        // Given -- W-088k2 case 3
        TradeOutcomeEntity undated = row("N", AssetType.EQUITY, 120, 100, 1);
        undated.setSellDate(null);

        // When / Then
        assertThatThrownBy(() -> summaryOf(List.of(undated)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The disposal N has no sell date");
    }

    @Test
    void summaryFor_whenARowHasMiscCharges_publishesThemApartFromTheCodes() {
        // When — row B's user-entered misc charge of 10 carries no code
        CapitalGainsSummary summary = summaryOf(List.of(rowsAtoC().get(1)));

        // Then — 600 fetched for 520; the misc on the sale side, beside its coded brokerage 3 and STT 0.60
        CapitalGainsBucket b = summary.buckets().getFirst();
        assertFetchedAndCost(b, 600.00, 520.00);
        assertCanonical(10.00, b.amounts().transfer().miscRupees());
        assertThat(b.amounts().transfer().rupeesByCode()).containsOnlyKeys("BROKERAGE", "STT");
    }

    @Test
    void summaryFor_whenAFundIsSoldBesideEquity_publishesItsOwnBucket() {
        // Given — the instrument registry has given the scheme no sub-class
        TradeOutcomeEntity fund = charged(row("D", AssetType.MUTUAL_FUND, 50, 40, 10), 0, 0);

        // When
        CapitalGainsSummary summary = summaryOf(List.of(rowsAtoC().get(2), fund));

        // Then — 50 × 10 = 500 fetched for 40 × 10 = 400, apart from the equity
        assertThat(summary.buckets()).hasSize(2);
        CapitalGainsBucket fundBucket = summary.buckets().stream()
                .filter(b -> b.assetType() == AssetType.MUTUAL_FUND).findFirst().orElseThrow();
        assertThat(fundBucket.instrumentSubClass()).isEqualTo(Optional.empty());
        assertFetchedAndCost(fundBucket, 500.00, 400.00);
    }

    @Test
    void summaryFor_whenARowBoreChargesWithoutABreakdown_isRefused() {
        // Given — a migrated row: broker charges 20, no breakdown, so what they were is unknown
        TradeOutcomeEntity migrated = charged(row("E", AssetType.EQUITY, 120, 100, 10), 0, 0);
        migrated.setBuyBrokerCharges(20);
        migrated.setBuyChargeBreakup(Map.of());
        when(tradeOutcomeRepository.findByEmailAndSellDateBetween(eq(EMAIL), any(), any()))
                .thenReturn(List.of(rowsAtoC().getFirst(), migrated));

        // When / Then
        assertThatThrownBy(() -> service.summaryFor(EMAIL, YEAR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 disposal(s) bore broker charges but kept no breakdown")
                .hasMessageContaining("STOCK-E on 2025-06-02");
    }

    @Test
    void summaryFor_whenTheYearIsInTheMigrationsForm_isRefused() {
        // When / Then
        assertThatThrownBy(() -> service.summaryFor(EMAIL, "FY2025-26"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("such as 2025-2026");
    }

    @Test
    void summaryFor_whenTheYearsAreNotConsecutive_isRefused() {
        // When / Then
        assertThatThrownBy(() -> service.summaryFor(EMAIL, "2025-2027"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2025-2027");
    }

    @Test
    void summaryFor_whenTheYearIs2025To2026_readsTheDaysEitherSideOfIt() {
        // When
        summaryOf(List.of());

        // Then — the finder excludes both ends, so 31 March 2025 and 1 April 2026 bound 1 April 2025 – 31 March 2026
        verify(tradeOutcomeRepository).findByEmailAndSellDateBetween(EMAIL, LocalDate.of(2025, 3, 31),
                LocalDate.of(2026, 4, 1));
    }

    @Test
    void summaryFor_whenNothingWasSold_publishesNoBuckets() {
        // When
        CapitalGainsSummary summary = summaryOf(List.of());

        // Then
        assertThat(summary.buckets()).isEmpty();
        assertThat(summary.financialYear()).isEqualTo(YEAR);
        assertThat(summary.portfolioComputationRef()).startsWith("trade_outcomes:");
    }

    @Test
    void summaryFor_whenARowsAmountChanges_namesAnotherComputation() {
        // Given
        String before = summaryOf(rowsAtoC()).portfolioComputationRef();
        String again = summaryOf(rowsAtoC()).portfolioComputationRef();
        List<TradeOutcomeEntity> changed = rowsAtoC();
        changed.get(2).setSellPrice(501);

        // When
        String after = summaryOf(changed).portfolioComputationRef();

        // Then
        assertThat(again).isEqualTo(before);
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void summaryFor_whenOnlyARowsChargeChanges_namesAnotherComputation() {
        // Given — the same sale and cost; only row C's sale-side STT differs, which can change the gain the engine nets
        String before = summaryOf(rowsAtoC()).portfolioComputationRef();
        List<TradeOutcomeEntity> changed = rowsAtoC();
        changed.get(2).setSellChargeBreakup(Map.of("BROKERAGE", 6.0, "STT", 1.10));

        // When
        String after = summaryOf(changed).portfolioComputationRef();

        // Then
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void summaryFor_whenARowRecordsEachSidesVenue_publishesThemApart() {
        // Given -- bought on BSE for delivery, sold on NSE for delivery (D-295)
        TradeOutcomeEntity row = soldWithStt(row("V", AssetType.EQUITY, 120, 100, 1));
        row.setBuyExchangeName("BSE");
        row.setBuySegment(TradeSegment.DELIVERY);
        row.setSellExchangeName("NSE");
        row.setSellSegment(TradeSegment.DELIVERY);

        // When
        CapitalGainsBucket only = summaryOf(List.of(row)).buckets().getFirst();

        // Then
        assertThat(only.acquisitionVenue()).isEqualTo(new TradeVenue(Optional.of("BSE"),
                Optional.of(TradeSegment.DELIVERY)));
        assertThat(only.transferVenue()).isEqualTo(new TradeVenue(Optional.of("NSE"),
                Optional.of(TradeSegment.DELIVERY)));
    }

    @Test
    void summaryFor_whenARowPredatesTheVenues_publishesThemUnrecorded() {
        // Given -- a row written before W-088j2b: no per-side venue, though the old exchange_name is set
        TradeOutcomeEntity row = soldWithStt(row("W", AssetType.EQUITY, 120, 100, 1));
        row.setExchangeName("NSE");

        // When
        CapitalGainsBucket only = summaryOf(List.of(row)).buckets().getFirst();

        // Then -- the old field is not read for tax: which side it described depended on the writer
        assertThat(only.acquisitionVenue()).isEqualTo(TradeVenue.UNRECORDED);
        assertThat(only.transferVenue()).isEqualTo(TradeVenue.UNRECORDED);
    }

    @Test
    void summaryFor_whenRowsDifferOnlyInTheSalesExchange_publishesThemApart() {
        // Given -- one key but for where the sale was entered into
        TradeOutcomeEntity onNse = soldWithStt(row("X", AssetType.EQUITY, 110, 100, 1));
        onNse.setSellExchangeName("NSE");
        TradeOutcomeEntity offMarket = soldWithStt(row("Y", AssetType.EQUITY, 130, 100, 1));

        // When
        CapitalGainsSummary summary = summaryOf(List.of(onNse, offMarket));

        // Then -- two buckets: 110 for 100 and 130 for 100, by hand
        assertThat(summary.buckets()).extracting(b -> b.transferVenue().exchange())
                .containsExactly(Optional.of("NSE"), Optional.empty());
        assertFetchedAndCost(summary.buckets().get(0), 110.00, 100.00);
        assertFetchedAndCost(summary.buckets().get(1), 130.00, 100.00);
    }
}
