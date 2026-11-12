package com.thiru.wealthlens.portfolio.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.AssetsRebuildReport;
import com.thiru.wealthlens.portfolio.dto.AssetsRebuildReport.LotComparison;
import com.thiru.wealthlens.portfolio.dto.AssetsRebuildReport.LotFigures;
import com.thiru.wealthlens.portfolio.dto.OrderTimeQuantity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** The replay's order of events and what it reads for a trade with no recorded flow (W-283a2, D-582); by hand. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetsRebuildTest {

    private static final String EMAIL = "rebuild@example.com";
    private static final LocalDate JAN_5 = LocalDate.of(2026, 1, 5);
    private static final LocalDate FEB_10 = LocalDate.of(2026, 2, 10);

    @Mock private TransactionRepository transactionRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private TradeOutcomeRepository tradeOutcomeRepository;
    @Mock private UserChargeService userChargeService;
    @Mock private PerformedCorporateActions performedCorporateActions;
    @Mock private CorporateActionService corporateActionService;
    @Mock private ChargeEngineProperties chargeEngineProperties;

    @InjectMocks private AssetsRebuild rebuild;

    @BeforeEach
    void setUp() {
        when(portfolioRepository.findByEmail(EMAIL)).thenReturn(List.of());
        when(tradeOutcomeRepository.findByEmail(EMAIL)).thenReturn(List.of());
        when(tradeOutcomeRepository.findByEmailAndSellChargeBreakupExists(EMAIL, true)).thenReturn(List.of());
        when(userChargeService.findForTransactions(eq(EMAIL), any())).thenReturn(Map.of());
        when(performedCorporateActions.of(anyString(), any()))
                .thenReturn(new PerformedCorporateActions.Replay(List.of(), List.of()));
    }

    @Test
    void rebuild_whenASaleIsDatedOnASplitsRecordDate_splitsFirst() {
        // Given -- the tie-break: buy 10 @ 100 (5 Jan), 6 @ 110 (20 Jan), sell 4 on 10 Feb; split 2:1 recorded 10 Feb
        transactions(trade("a", TransactionType.BUY, 10, 100, JAN_5, TradeFlow.V2),
                trade("b", TransactionType.BUY, 6, 110, LocalDate.of(2026, 1, 20), TradeFlow.V2),
                trade("s", TransactionType.SELL, 4, 130, FEB_10, TradeFlow.V2));
        CorporateActionEntity split = new CorporateActionEntity();
        split.setId("split-1");
        split.setType(CorporateActionType.STOCK_SPLIT);
        split.setStockCode("INFY");
        split.setRecordDate(FEB_10);
        split.setRatio("2:1");
        when(performedCorporateActions.of(anyString(), any())).thenReturn(new PerformedCorporateActions.Replay(
                List.of(new PerformedCorporateActions.BrokerAction(split, BrokerName.ZERODHA)), List.of()));

        // When
        AssetsRebuildReport report = rebuild.rebuild(UserMail.from(EMAIL), true);

        // Then -- split first: A 10 → 20 @ 50, B 6 → 12 @ 55; then 4 sold from A → 16. Sale first would leave
        // A (10 − 4) × 2 = 12
        LotFigures a = rebuilt(report, "a");
        LotFigures b = rebuilt(report, "b");
        assertEquals(16.0, a.quantity(), 1e-9);
        assertMoney(50.0, a.priceRupees());
        assertEquals(12.0, b.quantity(), 1e-9);
        assertMoney(55.0, b.priceRupees());
        assertEquals(List.of("STOCK_SPLIT INFY 2026-02-10 ZERODHA"), report.actionsApplied());
    }

    @Test
    void rebuild_whenTradesPredateTheFlow_readsTheirShapeFromWhatIsStored() {
        // Given -- two buys with no flow listed together on one stored lot (a V1 merge), and a sale whose row
        // carries no sell_charge_breakup (V1); the buy carries ₹30 over 20 units
        TransactionEntity first = trade("x", TransactionType.BUY, 10, 100, JAN_5, null);
        first.setBrokerCharges(30);
        transactions(first, trade("y", TransactionType.BUY, 10, 120, JAN_5, null),
                trade("z", TransactionType.SELL, 5, 130, FEB_10, null));
        AssetEntity storedLot = new AssetEntity();
        storedLot.setStockCode("INFY");
        storedLot.setBrokerName(BrokerName.ZERODHA);
        storedLot.setAccountHolder("Self");
        storedLot.setTransactionDate(JAN_5);
        storedLot.setQuantity(20.0);
        storedLot.setPrice(110);
        storedLot.setBuyTransactionIds(new ArrayList<>(List.of("x", "y")));
        when(portfolioRepository.findByEmail(EMAIL)).thenReturn(List.of(storedLot));
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setSourceSellTransactionId("z");
        when(tradeOutcomeRepository.findByEmail(EMAIL)).thenReturn(List.of(row));

        // When
        AssetsRebuildReport report = rebuild.rebuild(UserMail.from(EMAIL), true);

        // Then -- merged: 20 @ 110; V1 sale of 5 records ₹30 × 5 / 20 = ₹7.50 on the lot; 15 left
        LotFigures merged = rebuilt(report, "x");
        assertEquals(15.0, merged.quantity(), 1e-9);
        assertMoney(110.0, merged.priceRupees());
        assertMoney(7.50, merged.allocatedBuyChargesRupees());
        assertEquals(List.of("x", "y"), report.shapeFromStoredLots());
        assertEquals(List.of("z"), report.saleFlowFromOutcomes());
    }

    @Test
    void rebuild_whenATradeIsWaiting_skipsAndCountsIt() {
        // Given -- one held buy and one temporary
        TransactionEntity waiting = trade("t", TransactionType.BUY, 5, 100, JAN_5, TradeFlow.V2);
        waiting.setStatus(TransactionStatus.TEMPORARY);
        transactions(trade("a", TransactionType.BUY, 10, 100, JAN_5, TradeFlow.V2), waiting);

        // When
        AssetsRebuildReport report = rebuild.rebuild(UserMail.from(EMAIL), true);

        // Then -- one rebuilt lot, the temporary counted, never applied
        assertEquals(1, report.temporariesSkipped());
        assertEquals(1, report.lots().size());
        assertEquals(10.0, rebuilt(report, "a").quantity(), 1e-9);
    }

    @Test
    void rebuild_whenWritten_keepsAPairedLotsIdOrderTimesActionsAndCreation() {
        // Given -- a V2 buy of 10 stored as lot-1 with a wrong quantity of 8, an order time, an action and a creation
        transactions(trade("a", TransactionType.BUY, 10, 100, JAN_5, TradeFlow.V2));
        AssetEntity storedLot = new AssetEntity();
        storedLot.setId("lot-1");
        storedLot.setStockCode("INFY");
        storedLot.setBrokerName(BrokerName.ZERODHA);
        storedLot.setAccountHolder("Self");
        storedLot.setTransactionDate(JAN_5);
        storedLot.setQuantity(8.0);
        storedLot.setPrice(100);
        storedLot.setBuyTransactionIds(new ArrayList<>(List.of("a")));
        OrderTimeQuantity orderTime = new OrderTimeQuantity();
        orderTime.setQuantity(10.0);
        storedLot.setOrderTimeQuantities(new ArrayList<>(List.of(orderTime)));
        storedLot.getCorporateActions().add(new CorporateActionEntity());
        storedLot.getAuditMetadata().setCreatedAt(LocalDateTime.of(2026, 1, 5, 10, 0));
        when(portfolioRepository.findByEmail(EMAIL)).thenReturn(List.of(storedLot));

        // When
        AssetsRebuildReport report = rebuild.rebuild(UserMail.from(EMAIL), false);

        // Then -- the user's lots replaced by one: lot-1's id, order time, action and creation; the replay's 10 (C2a)
        verify(portfolioRepository).deleteByEmail(EMAIL);
        ArgumentCaptor<List<AssetEntity>> saved = ArgumentCaptor.captor();
        verify(portfolioRepository).saveAll(saved.capture());
        AssetEntity written = saved.getValue().getFirst();
        assertEquals("lot-1", written.getId());
        assertEquals(10.0, written.getQuantity(), 1e-9);
        assertEquals(List.of(orderTime), written.getOrderTimeQuantities());
        assertEquals(1, written.getCorporateActions().size());
        assertEquals(LocalDateTime.of(2026, 1, 5, 10, 0), written.getAuditMetadata().getCreatedAt());
        assertEquals(1, report.lotsWritten());
        assertEquals(1, report.lotsKeptById());
        assertEquals(List.of(), report.newLots());
    }

    @Test
    void rebuild_whenADryRun_writesNothing() {
        // Given
        transactions(trade("a", TransactionType.BUY, 10, 100, JAN_5, TradeFlow.V2));

        // When
        AssetsRebuildReport report = rebuild.rebuild(UserMail.from(EMAIL), true);

        // Then -- the lot would be new, and nothing is touched
        verify(portfolioRepository, never()).deleteByEmail(any());
        verify(portfolioRepository, never()).saveAll(any());
        assertEquals(0, report.lotsWritten());
        assertEquals(1, report.newLots().size());
    }

    private void transactions(TransactionEntity... trades) {
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(trades));
    }

    private static LotFigures rebuilt(AssetsRebuildReport report, String firstBuyId) {
        return report.lots().stream()
                .filter(lot -> lot.rebuilt().isPresent() && !lot.buyTransactionIds().isEmpty()
                        && lot.buyTransactionIds().getFirst().equals(firstBuyId))
                .map(LotComparison::rebuilt).findFirst().orElseThrow().orElseThrow();
    }

    private static TransactionEntity trade(String id, TransactionType type, double quantity, double price,
                                           LocalDate date, TradeFlow flow) {
        TransactionEntity trade = new TransactionEntity();
        trade.setId(id);
        trade.setEmail(EMAIL);
        trade.setStockCode("INFY");
        trade.setBrokerName(BrokerName.ZERODHA);
        trade.setAccountHolder("Self");
        trade.setTransactionType(type);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setTransactionDate(date);
        trade.setTradeFlow(flow);
        return trade;
    }
}
