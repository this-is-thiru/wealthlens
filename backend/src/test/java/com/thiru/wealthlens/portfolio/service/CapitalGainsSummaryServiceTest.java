package com.thiru.wealthlens.portfolio.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertCanonical;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
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
 * The published capital-gains read-model, from hand-worked rows (D-238): each gain is the sale value less the adjusted
 * cost less the deductible charges.
 */
@ExtendWith(MockitoExtension.class)
class CapitalGainsSummaryServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String YEAR = "2025-2026";

    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;

    @InjectMocks
    private CapitalGainsSummaryService service;

    /** A disposal, with no charges until {@link #charged} gives it some. */
    private static TradeOutcomeEntity row(String id, CapitalGainsType classification, AssetType assetType,
            double sellPrice, double buyPrice, double quantity) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setStockCode("STOCK-" + id);
        row.setCapitalGainsType(classification);
        row.setAssetType(assetType);
        row.setSellDate(LocalDate.of(2025, 6, 2));
        row.setSellPrice(sellPrice);
        row.setSellQuantity(quantity);
        row.setCorporateActionAdjustedBuyPrice(buyPrice);
        row.setBuyQuantity(quantity);
        return row;
    }

    /** The row with brokerage on each side, broken down, so its deductible part is known. */
    private static TradeOutcomeEntity charged(TradeOutcomeEntity row, double deductibleBuy, double deductibleSell) {
        row.setBuyBrokerCharges(deductibleBuy);
        row.setBuyChargeBreakup(Map.of("BROKERAGE", deductibleBuy));
        row.setDeductibleBuyCharges(deductibleBuy);
        row.setSellBrokerCharges(deductibleSell);
        row.setSellChargeBreakup(Map.of("BROKERAGE", deductibleSell));
        row.setDeductibleSellCharges(deductibleSell);
        return row;
    }

    /** Rows A, B (short-term equity) and C (long-term equity) of the plan's table, each sale bearing STT. */
    private static List<TradeOutcomeEntity> rowsAtoC() {
        TradeOutcomeEntity a = charged(row("A", CapitalGainsType.SHORT_TERM, AssetType.EQUITY, 120, 100, 10), 5, 7);
        a.setSellChargeBreakup(Map.of("BROKERAGE", 7.0, "STT", 1.20));
        a.setSellBrokerCharges(8.20);
        a.setNetProfit(178.80);
        TradeOutcomeEntity b = charged(row("B", CapitalGainsType.SHORT_TERM, AssetType.EQUITY, 150, 130, 4), 2, 3);
        b.setSellMiscCharges(10);
        b.setSellChargeBreakup(Map.of("BROKERAGE", 3.0, "STT", 0.60));
        TradeOutcomeEntity c = charged(row("C", CapitalGainsType.LONG_TERM, AssetType.EQUITY, 500, 300, 2), 4, 6);
        c.setSellChargeBreakup(Map.of("BROKERAGE", 6.0, "STT", 1.00));
        return List.of(a, b, c);
    }

    private CapitalGainsSummary summaryOf(List<TradeOutcomeEntity> rows) {
        when(tradeOutcomeRepository.findByEmailAndSellDateBetween(eq(EMAIL), any(), any())).thenReturn(rows);
        return service.summaryFor(EMAIL, YEAR);
    }

    private static CapitalGainsBucket bucket(CapitalGainsSummary summary, CapitalGainsType classification) {
        return summary.buckets().stream().filter(b -> b.classification() == classification).findFirst().orElseThrow();
    }

    @Test
    void summaryFor_whenRowsShareAClassification_sumsTheirGainsNetOfDeductibleCharges() {
        // When
        CapitalGainsSummary summary = summaryOf(rowsAtoC());

        // Then — A 1,200 − 1,000 − 5 − 7 = 188, B 600 − 520 − 2 − 3 = 75; C 1,000 − 600 − 4 − 6 = 390
        assertThat(summary.buckets()).hasSize(2);
        assertCanonical(263.00, bucket(summary, CapitalGainsType.SHORT_TERM).gainRupees());
        assertCanonical(390.00, bucket(summary, CapitalGainsType.LONG_TERM).gainRupees());
        assertThat(bucket(summary, CapitalGainsType.SHORT_TERM).assetType()).isEqualTo(AssetType.EQUITY);
    }

    @Test
    void summaryFor_whenARowPaidStt_doesNotDeductIt() {
        // When — row A's STT 1.20 is in its breakdown and in its netProfit of 178.80, not in its deductible charges
        CapitalGainsSummary summary = summaryOf(List.of(rowsAtoC().getFirst()));

        // Then
        assertCanonical(188.00, summary.buckets().getFirst().gainRupees());
    }

    @Test
    void summaryFor_whenTheBreakdownsShowStt_recordsItOnEachSide() {
        // Given — row A's sale bore STT 1.20, its buy only brokerage; row D's buy and sale both bore STT
        TradeOutcomeEntity d = charged(row("D", CapitalGainsType.SHORT_TERM, AssetType.EQUITY, 110, 100, 1), 0, 0);
        d.setBuyChargeBreakup(Map.of("STT", 0.10));
        d.setBuyBrokerCharges(0.10);
        d.setSellChargeBreakup(Map.of("STT", 0.11));
        d.setSellBrokerCharges(0.11);

        // When
        CapitalGainsSummary summary = summaryOf(List.of(rowsAtoC().getFirst(), d));

        // Then — two short-term equity buckets, apart by STT on the buy (D-241)
        assertThat(summary.buckets()).hasSize(2);
        assertThat(summary.buckets().get(0).sttOnAcquisition()).isFalse();
        assertThat(summary.buckets().get(0).sttOnTransfer()).isTrue();
        assertThat(summary.buckets().get(1).sttOnAcquisition()).isTrue();
        assertThat(summary.buckets().get(1).sttOnTransfer()).isTrue();
    }

    @Test
    void summaryFor_whenARowHasMiscCharges_doesNotDeductThem() {
        // When — row B's user-entered misc charge of 10 has no code, so nothing says it is deductible
        CapitalGainsSummary summary = summaryOf(List.of(rowsAtoC().get(1)));

        // Then
        assertCanonical(75.00, summary.buckets().getFirst().gainRupees());
    }

    @Test
    void summaryFor_whenAFundIsUnclassified_publishesItsOwnBucket() {
        // Given — the instrument registry has not classified the scheme (X1.3: published, blocked downstream)
        TradeOutcomeEntity fund = charged(row("D", CapitalGainsType.UNCLASSIFIED, AssetType.MUTUAL_FUND, 50, 40, 10), 0, 0);

        // When
        CapitalGainsSummary summary = summaryOf(List.of(rowsAtoC().get(2), fund));

        // Then — 500 − 400 = 100, apart from the equity
        assertThat(summary.buckets()).hasSize(2);
        CapitalGainsBucket unclassified = bucket(summary, CapitalGainsType.UNCLASSIFIED);
        assertThat(unclassified.assetType()).isEqualTo(AssetType.MUTUAL_FUND);
        assertThat(unclassified.instrumentSubClass()).isEqualTo(Optional.empty());
        assertCanonical(100.00, unclassified.gainRupees());
    }

    @Test
    void summaryFor_whenARowBoreChargesWithoutABreakdown_isRefused() {
        // Given — a migrated row: broker charges 20, no breakdown, so its deductible part reads 0 but is unknown
        TradeOutcomeEntity migrated = charged(row("E", CapitalGainsType.SHORT_TERM, AssetType.EQUITY, 120, 100, 10), 0, 0);
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
    void summaryFor_whenARowsGainChanges_namesAnotherComputation() {
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
}
