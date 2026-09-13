package com.thiru.wealthlens.portfolio.holding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TradeClassifierTest {

    private static final LocalDate BUY = LocalDate.of(2025, 1, 10);

    @Mock
    private HoldingPeriodService holdingPeriodService;

    @InjectMocks
    private TradeClassifier tradeClassifier;

    private void stubDelegate(CapitalGainsType type) {
        lenient().when(holdingPeriodService.classify(any(), any(), any(), any()))
                .thenReturn(new HoldingPeriodResolution(type, "EQUITY_LISTED", "held beyond 12 months"));
    }

    private static TradeClassificationQuery query(AssetType assetType, TradeSegment segment, LocalDate sell) {
        return new TradeClassificationQuery("INFY", assetType, segment, BUY, sell);
    }

    @Test
    @DisplayName("intraday equity is speculative business income, never capital gains")
    void classify_whenIntradayEquity_isSpeculative() {
        // Given -- the delegate would happily answer, which is the point: it must not be asked
        stubDelegate(CapitalGainsType.LONG_TERM);
        TradeClassificationQuery query = query(AssetType.EQUITY, TradeSegment.INTRADAY, BUY);

        // When
        TradeClassification classification = tradeClassifier.classify(query);

        // Then
        assertEquals(CapitalGainsType.SPECULATIVE, classification.capitalGainsType());
    }

    @Test
    @DisplayName("intraday short-circuits: holding period is not even consulted")
    void classify_whenIntradayEquity_doesNotConsultHoldingPeriod() {
        // Given
        stubDelegate(CapitalGainsType.LONG_TERM);
        TradeClassificationQuery query = query(AssetType.EQUITY, TradeSegment.INTRADAY, BUY);

        // When
        tradeClassifier.classify(query);

        // Then
        verify(holdingPeriodService, never()).classify(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a derivative is business income too, but not speculative — and has no bucket yet")
    void classify_whenDerivative_isUnclassifiedWithItsOwnReason() {
        // Given
        stubDelegate(CapitalGainsType.LONG_TERM);
        TradeClassificationQuery query = query(AssetType.EQUITY, TradeSegment.FUTURES, BUY.plusMonths(2));

        // When
        TradeClassification classification = tradeClassifier.classify(query);

        // Then
        assertEquals(CapitalGainsType.UNCLASSIFIED, classification.capitalGainsType());
        assertNotNull(classification.reason());
        verify(holdingPeriodService, never()).classify(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a delivery trade is classified by holding period, as before")
    void classify_whenDelivery_delegatesToHoldingPeriod() {
        // Given
        LocalDate sell = BUY.plusYears(2);
        when(holdingPeriodService.classify(eq(AssetType.EQUITY), isNull(), eq(BUY), eq(sell)))
                .thenReturn(new HoldingPeriodResolution(CapitalGainsType.LONG_TERM, "EQUITY_LISTED", "held beyond 12 months"));

        // When
        TradeClassification classification = tradeClassifier.classify(query(AssetType.EQUITY, TradeSegment.DELIVERY, sell));

        // Then
        assertEquals(CapitalGainsType.LONG_TERM, classification.capitalGainsType());
        assertEquals("held beyond 12 months", classification.reason());
    }

    @Test
    @DisplayName("intraday on a non-equity asset type is not speculative — that rule is equity's")
    void classify_whenIntradayNonEquity_isNotSpeculative() {
        // Given
        when(holdingPeriodService.classify(any(), any(), any(), any()))
                .thenReturn(new HoldingPeriodResolution(CapitalGainsType.UNCLASSIFIED, null, "no policy"));

        // When
        TradeClassification classification = tradeClassifier.classify(query(AssetType.MUTUAL_FUND, TradeSegment.INTRADAY, BUY));

        // Then
        assertEquals(CapitalGainsType.UNCLASSIFIED, classification.capitalGainsType());
    }

    @Test
    @DisplayName("the sub-class is not available yet (D7), so it is reported as null rather than guessed")
    void classify_whenMutualFund_reportsNoSubClass() {
        // Given
        when(holdingPeriodService.classify(any(), any(), any(), any()))
                .thenReturn(new HoldingPeriodResolution(CapitalGainsType.UNCLASSIFIED, null, "no policy"));

        // When
        TradeClassification classification = tradeClassifier.classify(
                query(AssetType.MUTUAL_FUND, TradeSegment.DELIVERY, BUY.plusYears(3)));

        // Then
        assertNull(classification.subClass());
    }
}
