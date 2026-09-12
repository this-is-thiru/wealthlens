package com.thiru.wealthlens.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.service.ChargeDeductibilityService;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodResolution;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodService;
import com.thiru.wealthlens.portfolio.holding.TradeClassifier;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradeOutcomeRecorderTest {

    private static final String EMAIL = "test@example.com";
    private static final LocalDate BUY_DATE = LocalDate.of(2024, 1, 10);
    private static final LocalDate SELL_DATE = LocalDate.of(2025, 6, 10);

    @Mock private TradeOutcomeService tradeOutcomeService;
    @Mock private HoldingPeriodService holdingPeriodService;
    @Mock private ChargeDeductibilityService chargeDeductibilityService;
    @Mock private UserChargeService userChargeService;
    @Mock private TransactionRepository transactionRepository;

    private TradeOutcomeRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new TradeOutcomeRecorder(tradeOutcomeService, new TradeClassifier(holdingPeriodService),
                chargeDeductibilityService, userChargeService, transactionRepository);
        when(holdingPeriodService.classify(any(), any(), any(), any()))
                .thenReturn(new HoldingPeriodResolution(CapitalGainsType.LONG_TERM, "EQUITY_LISTED", "held beyond 12 months"));
    }

    private static AssetEntity lot(String txnId, double quantity, double price, double brokerCharges) {
        AssetEntity asset = new AssetEntity();
        asset.setId("asset-" + txnId);
        asset.setEmail(EMAIL);
        asset.setStockCode("INFY");
        asset.setStockName("Infosys");
        asset.setExchangeName("NSE");
        asset.setBrokerName(BrokerName.ZERODHA);
        asset.setAssetType(AssetType.EQUITY);
        asset.setSegment(TradeSegment.DELIVERY);
        asset.setPrice(price);
        asset.setQuantity(quantity);
        asset.setBrokerCharges(brokerCharges);
        asset.setTransactionDate(BUY_DATE);
        asset.getBuyTransactionIds().add(txnId);
        return asset;
    }

    private static AssetRequest sell(double quantity, double price, TradeSegment segment) {
        AssetRequest request = new AssetRequest();
        request.setEmail(EMAIL);
        request.setStockCode("INFY");
        request.setStockName("Infosys");
        request.setExchangeName("NSE");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.EQUITY);
        request.setSegment(segment);
        request.setPrice(price);
        request.setQuantity(quantity);
        request.setTransactionDate(SELL_DATE);
        return request;
    }

    private static ChargeComputation computation(Map<String, Double> byCode) {
        List<ChargeLine> lines = byCode.entrySet().stream()
                .map(e -> {
                    ChargeLine line = new ChargeLine();
                    line.setCode(e.getKey());
                    line.setAmount(e.getValue());
                    return line;
                }).toList();
        double total = byCode.values().stream().mapToDouble(Double::doubleValue).sum();
        return new ChargeComputation("sched-1", "ZER-EQ", null, ChargeResolution.RESOLVED, lines, total);
    }

    private List<TradeOutcomeContext> captureSaved(int expected) {
        ArgumentCaptor<TradeOutcomeContext> captor = ArgumentCaptor.forClass(TradeOutcomeContext.class);
        verify(tradeOutcomeService, times(expected)).saveTradeOutcome(any(UserMail.class), captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("a V2 sell writes one itemised row per matched lot -- it wrote none at all before")
    void record_whenSellSpansTwoLots_writesARowForEach() {
        // Given
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 20.0), 2.0, 2.0),
                new TradeOutcomeRecorder.MatchedLot(lot("buy-2", 1.0, 150.0, 30.0), 2.0, 3.0));

        // When
        recorder.record(UserMail.from(EMAIL), sell(4.0, 200.0, TradeSegment.DELIVERY), "sell-1",
                lots, Optional.of(computation(Map.of("BROKERAGE", 20.0, "STT", 100.0))));

        // Then
        assertEquals(2, captureSaved(2).size());
    }

    @Test
    @DisplayName("the sell charge is pro-rated across lots by quantity, and so is its breakup")
    void record_whenSellSpansTwoLots_proRatesTheSellChargeByQuantity() {
        // Given -- a 4-unit sell taking 1 unit from one lot and 3 from another
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 20.0), 1.0, 1.0),
                new TradeOutcomeRecorder.MatchedLot(lot("buy-2", 0.0, 150.0, 30.0), 3.0, 3.0));
        Map<String, Double> byCode = new LinkedHashMap<>();
        byCode.put("BROKERAGE", 20.0);
        byCode.put("STT", 100.0);

        // When
        recorder.record(UserMail.from(EMAIL), sell(4.0, 200.0, TradeSegment.DELIVERY), "sell-1",
                lots, Optional.of(computation(byCode)));

        // Then
        List<TradeOutcomeContext> saved = captureSaved(2);
        MoneyAssert.assertMoney("quarter share", 30.0, saved.getFirst().getSellBrokerCharges());
        MoneyAssert.assertMoney("three-quarter share", 90.0, saved.get(1).getSellBrokerCharges());
        MoneyAssert.assertMoney("STT quarter", 25.0, saved.getFirst().getSellChargeBreakup().get("STT"));
        MoneyAssert.assertMoney("STT three quarters", 75.0, saved.get(1).getSellChargeBreakup().get("STT"));
    }

    @Test
    @DisplayName("the sell charge comes from the engine, not from the deprecated user-entered field")
    void record_whenTheRequestCarriesAManualCharge_theEngineFigureWins() {
        // Given
        AssetRequest request = sell(1.0, 200.0, TradeSegment.DELIVERY);
        request.setBrokerCharges(999.0);
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 20.0), 1.0, 1.0));

        // When
        recorder.record(UserMail.from(EMAIL), request, "sell-1", lots,
                Optional.of(computation(Map.of("BROKERAGE", 12.0))));

        // Then
        MoneyAssert.assertMoney(12.0, captureSaved(1).getFirst().getSellBrokerCharges());
    }

    @Test
    @DisplayName("the buy breakup comes from what the engine recorded against that buy transaction")
    void record_whenTheBuyWasPriced_carriesItsBreakupOntoTheRow() {
        // Given
        UserChargeEntity buyCharges = new UserChargeEntity();
        buyCharges.setAmountByCode(new LinkedHashMap<>(Map.of("BROKERAGE", 20.0, "GST", 3.6)));
        when(userChargeService.findOptionalForTransaction(EMAIL, "buy-1")).thenReturn(Optional.of(buyCharges));
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 23.6), 1.0, 1.0));

        // When
        recorder.record(UserMail.from(EMAIL), sell(1.0, 200.0, TradeSegment.DELIVERY), "sell-1",
                lots, Optional.of(computation(Map.of("BROKERAGE", 12.0))));

        // Then
        MoneyAssert.assertMoney(20.0, captureSaved(1).getFirst().getBuyChargeBreakup().get("BROKERAGE"));
    }

    @Test
    @DisplayName("a buy made before the engine was on has no charge row, and the sell still records")
    void record_whenTheBuyWasNeverPriced_stillWritesTheRow() {
        // Given -- ADR-32: those buys are never re-driven, so this is permanent, not transitional
        when(userChargeService.findOptionalForTransaction(anyString(), anyString())).thenReturn(Optional.empty());
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 20.0), 1.0, 1.0));

        // When
        recorder.record(UserMail.from(EMAIL), sell(1.0, 200.0, TradeSegment.DELIVERY), "sell-1",
                lots, Optional.of(computation(Map.of("BROKERAGE", 12.0))));

        // Then
        TradeOutcomeContext row = captureSaved(1).getFirst();
        assertEquals(Map.of(), row.getBuyChargeBreakup());
        MoneyAssert.assertMoney("the lumped total still survives", 20.0, row.getBuyBrokerCharges());
    }

    @Test
    @DisplayName("deductible cost is a sum over codes, and excludes STT")
    void record_recordsDeductibleCostSeparatelyFromTheTotal() {
        // Given
        when(chargeDeductibilityService.deductibleTotal(any())).thenReturn(20.0);
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 20.0), 1.0, 1.0));

        // When
        recorder.record(UserMail.from(EMAIL), sell(1.0, 200.0, TradeSegment.DELIVERY), "sell-1",
                lots, Optional.of(computation(Map.of("BROKERAGE", 20.0, "STT", 100.0))));

        // Then
        MoneyAssert.assertMoney(20.0, captureSaved(1).getFirst().getDeductibleSellCharges());
    }

    @Test
    @DisplayName("intraday equity is recorded as speculative, with its segment on the row")
    void record_whenIntraday_classifiesAsSpeculativeAndRecordsTheSegment() {
        // Given
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 20.0), 1.0, 1.0));

        // When
        recorder.record(UserMail.from(EMAIL), sell(1.0, 200.0, TradeSegment.INTRADAY), "sell-1",
                lots, Optional.of(computation(Map.of("BROKERAGE", 12.0))));

        // Then
        TradeOutcomeContext row = captureSaved(1).getFirst();
        assertEquals(CapitalGainsType.SPECULATIVE, row.getCapitalGainsType());
        assertEquals(TradeSegment.INTRADAY, row.getSegment());
    }

    @Test
    @DisplayName("the sub-class is recorded as unknown rather than guessed (D7)")
    void record_recordsTheSubClassAsUnknown() {
        // Given
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 20.0), 1.0, 1.0));

        // When
        recorder.record(UserMail.from(EMAIL), sell(1.0, 200.0, TradeSegment.DELIVERY), "sell-1",
                lots, Optional.of(computation(Map.of("BROKERAGE", 12.0))));

        // Then
        assertNull(captureSaved(1).getFirst().getInstrumentSubClass());
    }

    @Test
    @DisplayName("when the engine declined to price the sell, the row is still written with no charge")
    void record_whenTheEngineDeclined_writesTheRowWithNoSellCharge() {
        // Given
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 20.0), 1.0, 1.0));

        // When
        recorder.record(UserMail.from(EMAIL), sell(1.0, 200.0, TradeSegment.DELIVERY), "sell-1",
                lots, Optional.empty());

        // Then
        TradeOutcomeContext row = captureSaved(1).getFirst();
        MoneyAssert.assertNoCharge(row.getSellBrokerCharges());
        assertEquals(Map.of(), row.getSellChargeBreakup());
    }

    @Test
    @DisplayName("pro-rated amounts are canonical to paise, not raw division output (TL-8)")
    void record_whenProRatingAcrossThreeLots_writesCanonicalAmounts() {
        // Given -- a 3-way split of Rs.100, which does not divide evenly into paise
        List<TradeOutcomeRecorder.MatchedLot> lots = List.of(
                new TradeOutcomeRecorder.MatchedLot(lot("buy-1", 0.0, 100.0, 10.0), 1.0, 1.0),
                new TradeOutcomeRecorder.MatchedLot(lot("buy-2", 0.0, 100.0, 10.0), 1.0, 1.0),
                new TradeOutcomeRecorder.MatchedLot(lot("buy-3", 0.0, 100.0, 10.0), 1.0, 1.0));

        // When
        recorder.record(UserMail.from(EMAIL), sell(3.0, 200.0, TradeSegment.DELIVERY), "sell-1",
                lots, Optional.of(computation(Map.of("BROKERAGE", 100.0))));

        // Then -- every stored amount must equal its own two-decimal rounding
        for (TradeOutcomeContext row : captureSaved(3)) {
            MoneyAssert.assertCanonicalToPaise("sellBrokerCharges", row.getSellBrokerCharges());
            MoneyAssert.assertCanonicalToPaise("buyBrokerCharges", row.getBuyBrokerCharges());
            MoneyAssert.assertCanonicalToPaise("totalBuyValue", row.getTotalBuyValue());
            MoneyAssert.assertCanonicalToPaise("totalSellValue", row.getTotalSellValue());
            MoneyAssert.assertCanonicalToPaise("netProfit", row.getNetProfit());
            row.getSellChargeBreakup().forEach(MoneyAssert::assertCanonicalToPaise);
        }
    }

}
