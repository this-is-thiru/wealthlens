package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodResolution;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodService;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.portfolio.service.ChargeRecordingGateway;
import com.thiru.wealthlens.portfolio.service.MongoTemplateService;
import com.thiru.wealthlens.portfolio.service.PortfolioService;
import com.thiru.wealthlens.portfolio.service.ProfitAndLossService;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.portfolio.service.TradeOutcomeRecorder;
import com.thiru.wealthlens.portfolio.service.TradeOutcomeService;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.dto.RedriveResult;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private ProfitAndLossService profitAndLossService;

    @Mock
    private MongoTemplateService mongoTemplateService;

    @Mock
    private TradeOutcomeService tradeOutcomeService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TemporaryTransactionService temporaryTransactionService;

    @Mock
    private ChargeRecordingGateway chargeRecordingGateway;

    @Mock
    private HoldingPeriodService holdingPeriodService;

    @Mock
    private TradeOutcomeRecorder tradeOutcomeRecorder;

    private TestablePortfolioService portfolioService;
    private UserMail userMail;

    @BeforeEach
    void setUp() {
        portfolioService = testableService(new ChargeEngineProperties(true, true, false));
        userMail = UserMail.from("test@example.com");
    }

    /**
     * A testable subclass of PortfolioService that allows overriding
     * the behaviour of methods that are difficult to mock in tests.
     */
    private TestablePortfolioService testableService(ChargeEngineProperties properties) {
        return new TestablePortfolioService(
                portfolioRepository,
                transactionService,
                profitAndLossService,
                mongoTemplateService,
                tradeOutcomeService,
                transactionRepository,
                temporaryTransactionService,
                chargeRecordingGateway,
                properties,
                holdingPeriodService,
                tradeOutcomeRecorder
        );
    }

    static class TestablePortfolioService extends PortfolioService {
        private int callCount;
        private int failFromCall;
        private RuntimeException throwOnCall;

        TestablePortfolioService(
                PortfolioRepository portfolioRepository,
                TransactionService transactionService,
                ProfitAndLossService profitAndLossService,
                MongoTemplateService mongoTemplateService,
                TradeOutcomeService tradeOutcomeService,
                TransactionRepository transactionRepository,
                TemporaryTransactionService temporaryTransactionService,
                ChargeRecordingGateway chargeRecordingGateway,
                ChargeEngineProperties chargeEngineProperties,
                HoldingPeriodService holdingPeriodService, TradeOutcomeRecorder tradeOutcomeRecorder) {
            super(transactionService, portfolioRepository, profitAndLossService,
                    mongoTemplateService, tradeOutcomeService, transactionRepository,
                    temporaryTransactionService, chargeRecordingGateway, chargeEngineProperties,
                    holdingPeriodService, tradeOutcomeRecorder);
        }

        void resetAddTransactionBehaviour() {
            this.callCount = 0;
            this.failFromCall = -1;
            this.throwOnCall = null;
        }

        /**
         * Call N (1-based) will throw the given exception. Calls before N succeed.
         */
        void setAddTransactionThrowsOnCall(int callNumber, RuntimeException ex) {
            this.failFromCall = callNumber;
            this.throwOnCall = ex;
        }

        @Override
        public String addTransaction(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions) {
            callCount++;
            if (callCount == failFromCall && throwOnCall != null) {
                throw throwOnCall;
            }
            // itemFiltered stays empty → adds to succeeded
            return "Stock buy added to portfolio";
        }
    }

    /**
     * Subclass of PortfolioService that does NOT override addTransaction,
     * allowing tests to exercise the real addTransaction flow (including temp-txn checks).
     */
    static class RealPortfolioService extends PortfolioService {
        RealPortfolioService(
                PortfolioRepository portfolioRepository,
                TransactionService transactionService,
                ProfitAndLossService profitAndLossService,
                MongoTemplateService mongoTemplateService,
                TradeOutcomeService tradeOutcomeService,
                TransactionRepository transactionRepository,
                TemporaryTransactionService temporaryTransactionService,
                ChargeRecordingGateway chargeRecordingGateway,
                ChargeEngineProperties chargeEngineProperties,
                HoldingPeriodService holdingPeriodService,
                TradeOutcomeRecorder tradeOutcomeRecorder) {
            super(transactionService, portfolioRepository, profitAndLossService,
                    mongoTemplateService, tradeOutcomeService, transactionRepository,
                    temporaryTransactionService, chargeRecordingGateway, chargeEngineProperties,
                    holdingPeriodService, tradeOutcomeRecorder);
        }

        @Override
        public String addTransaction(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions) {
            // Use real implementation; dependencies are mocked
            return super.addTransaction(userMail, assetRequest, filteredOutTransactions);
        }
    }

    @Test
    void addTransaction_whenTempTransactionsExist_throwsBadRequestException() {
        // Given: use real service so check is exercised
        RealPortfolioService realService = new RealPortfolioService(
                portfolioRepository, transactionService, profitAndLossService,
                mongoTemplateService, tradeOutcomeService, transactionRepository,
                temporaryTransactionService, chargeRecordingGateway,
                new ChargeEngineProperties(true, true, false), holdingPeriodService, tradeOutcomeRecorder);
        when(temporaryTransactionService.hasTemporaryTransactions(userMail)).thenReturn(true);
        AssetRequest request = createAssetRequest("STOCK1");

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> realService.addTransaction(userMail, request, new ArrayList<>()));
        assertEquals("There are pending temporary transactions. Please redrive them before adding a new transaction.",
                ex.getMessage());
    }

    @Test
    void addTransaction_whenNoTempTransactions_continuesNormally() {
        // Given: use real service so check is exercised
        RealPortfolioService realService = new RealPortfolioService(
                portfolioRepository, transactionService, profitAndLossService,
                mongoTemplateService, tradeOutcomeService, transactionRepository,
                temporaryTransactionService, chargeRecordingGateway,
                new ChargeEngineProperties(true, true, false), holdingPeriodService, tradeOutcomeRecorder);
        when(temporaryTransactionService.hasTemporaryTransactions(userMail)).thenReturn(false);
        AssetRequest request = createAssetRequest("STOCK1");

        // When / Then
        assertDoesNotThrow(() -> realService.addTransaction(userMail, request, new ArrayList<>()));
        verify(temporaryTransactionService).hasTemporaryTransactions(userMail);
    }

    @Test
    void uploadTransactions_whenTempTransactionsExist_throwsBadRequestException() {
        // Given: reset any prior mocks so hasTemporaryTransactions returns true
        reset(temporaryTransactionService);
        when(temporaryTransactionService.hasTemporaryTransactions(userMail)).thenReturn(true);

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> portfolioService.uploadTransactions(userMail, "Q1", null));
        assertEquals("There are pending temporary transactions. Please redrive them before uploading transactions. Some transactions may be blocked due to pending corporate actions during upload.",
                ex.getMessage());
    }

    @Test
    void uploadTransactions_whenNoTempTransactions_continuesNormally() {
        // Given: reset any prior mocks so hasTemporaryTransactions returns false
        reset(temporaryTransactionService);
        when(temporaryTransactionService.hasTemporaryTransactions(userMail)).thenReturn(false);

        // When / Then — will fail later at parsing since file is null, but we only verify the temp txn check passes
        assertThrows(NullPointerException.class,
                () -> portfolioService.uploadTransactions(userMail, "Q1", null));
        verify(temporaryTransactionService).hasTemporaryTransactions(userMail);
    }

    @Test
    void redrive_allSucceed() {
        // Given: 3 temp transactions, none filtered
        List<TransactionEntity> tempTransactions = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TransactionEntity txn = new TransactionEntity();
            txn.setId("temp-id-" + i);
            txn.setEmail(userMail.getEmail());
            txn.setAssetRequest(createAssetRequest("STOCK" + i));
            tempTransactions.add(txn);
        }

        when(temporaryTransactionService.getAllTemporaryTransactions(userMail))
                .thenReturn(tempTransactions);
        when(temporaryTransactionService.filterOutTransaction(any(), any(), eq(true)))
                .thenReturn(false);

        // When
        RedriveResult result = portfolioService.redriveTemporaryTransactions(userMail);

        // Then
        assertEquals(3, result.getSucceeded().size());
        assertTrue(result.getSucceeded().contains("temp-id-1"));
        assertTrue(result.getSucceeded().contains("temp-id-2"));
        assertTrue(result.getSucceeded().contains("temp-id-3"));
        assertTrue(result.getFailed().isEmpty());
        assertTrue(result.getStillFiltered().isEmpty());
        assertTrue(result.getFilteredOut().isEmpty());

        // Verify transactionRepository.saveAll was called once
        verify(transactionRepository).findAllById(result.getSucceeded());
        verify(transactionRepository).saveAll(anyList());
    }

    @Test
    void redrive_allStillFiltered() {
        // Given: 3 temp transactions, all still filtered by corporate action
        List<TransactionEntity> tempTransactions = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TransactionEntity txn = new TransactionEntity();
            txn.setId("temp-id-" + i);
            txn.setEmail(userMail.getEmail());
            txn.setAssetRequest(createAssetRequest("STOCK" + i));
            tempTransactions.add(txn);
        }

        when(temporaryTransactionService.getAllTemporaryTransactions(userMail))
                .thenReturn(tempTransactions);
        // All filtered - checkCorporateAction=true returns true
        when(temporaryTransactionService.filterOutTransaction(any(), any(), eq(true)))
                .thenReturn(true);

        // When
        RedriveResult result = portfolioService.redriveTemporaryTransactions(userMail);

        // Then
        assertTrue(result.getSucceeded().isEmpty());
        assertTrue(result.getFailed().isEmpty());
        assertEquals(3, result.getStillFiltered().size());
        assertTrue(result.getStillFiltered().contains("temp-id-1"));
        assertTrue(result.getStillFiltered().contains("temp-id-2"));
        assertTrue(result.getStillFiltered().contains("temp-id-3"));
        assertTrue(result.getFilteredOut().isEmpty());

        // No saves should happen since nothing succeeded
        verify(transactionRepository, never()).saveAll(anyList());
    }

    private AssetRequest createAssetRequest(String stockCode) {
        AssetRequest request = new AssetRequest();
        request.setStockCode(stockCode);
        request.setBrokerName(BrokerName.ZERODHA);
        request.setTransactionType(TransactionType.BUY);
        request.setPrice(100.0);
        request.setQuantity(10.0);
        request.setTransactionDate(LocalDate.now());
        request.setEmail(userMail.getEmail());
        return request;
    }

    // ========================================
    // Chunk 10a — AC-10: the computed total drives cost basis
    //
    // V2 only. buyStock/sellStock (V1) are unused and deliberately untouched, which is why
    // buyStockV2 no longer shares updateBrokerChargesAndProfitAndLoss with them.
    // ========================================

    private static final double ENTERED_CHARGES = 125.50;
    private static final double COMPUTED_CHARGES = 118.74;

    private AssetRequest buyRequest() {
        AssetRequest request = new AssetRequest();
        request.setStockCode("RELIANCE");
        request.setStockName("Reliance Industries");
        request.setExchangeName("NSE");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.EQUITY);
        request.setTransactionType(TransactionType.BUY);
        request.setAccountType(AccountType.SELF);
        request.setAccountHolder("self");
        request.setQuantity(100D);
        request.setPrice(1000D);
        request.setBrokerCharges(ENTERED_CHARGES);
        request.setTransactionDate(LocalDate.of(2025, 6, 10));
        return request;
    }

    private static ChargeComputation computed() {
        return new ChargeComputation("sched-1", "ZERODHA_EQ_DELIVERY_2025_04", null,
                ChargeResolution.RESOLVED, List.of(), COMPUTED_CHARGES);
    }

    private AssetEntity savedAsset() {
        ArgumentCaptor<AssetEntity> captor = ArgumentCaptor.forClass(AssetEntity.class);
        verify(portfolioRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void buyStockV2_whenAuthoritative_setsCostBasisFromTheComputedTotal() {
        // Given
        portfolioService = testableService(new ChargeEngineProperties(true, true, true));
        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.of(computed()));

        // When
        portfolioService.buyStockV2(userMail, "txn-1", buyRequest());

        // Then — the engine's figure, not the one the user typed
        assertEquals(COMPUTED_CHARGES, savedAsset().getBrokerCharges(), 0.001);
    }

    @Test
    void buyStockV2_whenNotAuthoritative_keepsTheEnteredChargesUntouched() {
        // Given — the default, and what every existing deployment runs
        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.of(computed()));

        // When
        portfolioService.buyStockV2(userMail, "txn-1", buyRequest());

        // Then
        assertEquals(ENTERED_CHARGES, savedAsset().getBrokerCharges(), 0.001);
    }

    /**
     * The trap the checklist names: the charge has to be computed before the lot is written, or the
     * cost basis saved is the stale one. Asserted by ordering, not by the value alone.
     */
    @Test
    void buyStockV2_computesTheChargeBeforeItSavesTheLot() {
        // Given
        portfolioService = testableService(new ChargeEngineProperties(true, true, true));
        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.of(computed()));

        // When
        portfolioService.buyStockV2(userMail, "txn-1", buyRequest());

        // Then
        InOrder inOrder = inOrder(chargeRecordingGateway, portfolioRepository);
        inOrder.verify(chargeRecordingGateway).record(any(), any());
        inOrder.verify(portfolioRepository).save(any(AssetEntity.class));
    }

    /**
     * No card for the period, or the engine switched off, and the user's own figure is all there is.
     * Overwriting it with a zero would silently erase a cost the trade really incurred.
     */
    @Test
    void buyStockV2_whenTheEngineComputedNothing_leavesTheEnteredChargesAlone() {
        // Given
        portfolioService = testableService(new ChargeEngineProperties(true, true, true));
        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.empty());

        // When
        portfolioService.buyStockV2(userMail, "txn-1", buyRequest());

        // Then
        assertEquals(ENTERED_CHARGES, savedAsset().getBrokerCharges(), 0.001);
    }

    /** The engine runs once per trade: the gateway is called here, so P&L must not call it again. */
    @Test
    void buyStockV2_handsTheComputationToProfitAndLossRatherThanLettingItRecompute() {
        // Given
        portfolioService = testableService(new ChargeEngineProperties(true, true, true));
        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.of(computed()));

        // When
        portfolioService.buyStockV2(userMail, "txn-1", buyRequest());

        // Then
        verify(chargeRecordingGateway, times(1)).record(any(), any());
        verify(profitAndLossService).updateProfitAndLoss(any(), any(ProfitLossContext.class), eq(Optional.of(computed())));
    }

    // ========================================
    // Request validation
    // ========================================

    /**
     * The guard returned early when the request carried no email, so everything below it — the only
     * quantity check there was — never ran. A trade with no email and no quantity was accepted.
     */
    @Test
    void addTransactionV2_whenTheRequestHasNoEmail_stillValidatesTheRest() {
        // Given
        AssetRequest request = buyRequest();
        request.setEmail(null);
        request.setQuantity(0D);

        // When / Then
        assertThrows(BadRequestException.class,
                () -> portfolioService.addTransactionV2(userMail, request, new ArrayList<>()));
    }

    /**
     * {@code equal(quantity, 0)} rejected zero and let everything else through. A negative buy
     * creates a negative holding; a negative sell increases one.
     */
    @Test
    void addTransactionV2_whenQuantityIsNegative_isRejected() {
        // Given
        AssetRequest request = buyRequest();
        request.setQuantity(-5D);

        // When / Then
        assertThrows(BadRequestException.class,
                () -> portfolioService.addTransactionV2(userMail, request, new ArrayList<>()));
    }

    /** Price was never checked at all. A negative price inverts the cost basis. */
    @Test
    void addTransactionV2_whenPriceIsNegative_isRejected() {
        // Given
        AssetRequest request = buyRequest();
        request.setPrice(-100D);

        // When / Then
        assertThrows(BadRequestException.class,
                () -> portfolioService.addTransactionV2(userMail, request, new ArrayList<>()));
    }

    /**
     * A future-dated trade resolves whatever rate card is open-ended today and is charged against
     * rates that may not apply when it settles — and it cannot be a real trade.
     */
    @Test
    void addTransactionV2_whenDatedInTheFuture_isRejected() {
        // Given
        AssetRequest request = buyRequest();
        request.setTransactionDate(LocalDate.now().plusDays(1));

        // When / Then
        assertThrows(BadRequestException.class,
                () -> portfolioService.addTransactionV2(userMail, request, new ArrayList<>()));
    }

    /** A zero price is legitimate: bonus shares and split allotments are issued free. */
    @Test
    void addTransactionV2_whenPriceIsZero_isAccepted() {
        // Given
        AssetRequest request = buyRequest();
        request.setPrice(0D);
        when(temporaryTransactionService.filterOutTransaction(any(), any())).thenReturn(null);
        when(transactionService.addTransaction(any(), any())).thenReturn("txn-free");
        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.empty());

        // When / Then
        assertDoesNotThrow(() -> portfolioService.addTransactionV2(userMail, request, new ArrayList<>()));
    }

    // ========================================
    // TL-6 -- the V2 sell writes itemised trade outcomes; V1 keeps its own path
    // ========================================

    private static AssetEntity sellableLot(String txnId, double quantity, double price, LocalDate buyDate) {
        AssetEntity asset = new AssetEntity();
        asset.setId("asset-" + txnId);
        asset.setEmail("test@example.com");
        asset.setStockCode("STOCK1");
        asset.setAssetType(AssetType.EQUITY);
        asset.setPrice(price);
        asset.setQuantity(quantity);
        asset.setTransactionDate(buyDate);
        asset.getBuyTransactionIds().add(txnId);
        return asset;
    }

    @Test
    void updateQuantityBySavingReportAndProfitAndLoss1_recordsTradeOutcomesForEveryConsumedLot() {
        // Given -- a 4-unit sell across a 3-unit lot and a 5-unit lot
        List<AssetEntity> lots = new ArrayList<>(List.of(
                sellableLot("buy-1", 3.0, 100.0, LocalDate.of(2024, 1, 10)),
                sellableLot("buy-2", 5.0, 120.0, LocalDate.of(2024, 6, 10))));
        AssetRequest sell = createAssetRequest("STOCK1");
        sell.setQuantity(4.0);
        sell.setPrice(200.0);
        sell.setTransactionDate(LocalDate.of(2025, 8, 10));
        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.empty());

        // When
        portfolioService.updateQuantityBySavingReportAndProfitAndLoss1(userMail, "sell-1", lots, sell);

        // Then -- both lots reach the recorder, with the partially consumed one carrying its
        // original quantity so buy-side charges can be pro-rated against the right denominator
        ArgumentCaptor<List<TradeOutcomeRecorder.MatchedLot>> captor = ArgumentCaptor.captor();
        verify(tradeOutcomeRecorder).record(eq(userMail), eq(sell), eq("sell-1"), captor.capture(), any());
        List<TradeOutcomeRecorder.MatchedLot> matched = captor.getValue();
        assertEquals(2, matched.size());
        assertEquals(3.0, matched.get(0).quantity(), 0.001);
        assertEquals(1.0, matched.get(1).quantity(), 0.001);
        assertEquals(5.0, matched.get(1).originalQuantity(), 0.001);
    }

    @Test
    void updateQuantityBySavingReportAndProfitAndLoss_v1_doesNotRecordTradeOutcomesThroughTheNewPath() {
        // Given -- V1 is in live use and writes its outcomes through toTradeOutcomeContext.
        // If it ever reaches the recorder as well, every V1 sell is double-counted.
        List<AssetEntity> lots = new ArrayList<>(List.of(
                sellableLot("buy-1", 5.0, 100.0, LocalDate.of(2024, 1, 10))));
        AssetRequest sell = createAssetRequest("STOCK1");
        sell.setQuantity(2.0);
        sell.setPrice(200.0);
        sell.setTransactionDate(LocalDate.of(2025, 8, 10));

        when(portfolioRepository
                .findByEmailAndStockCodeAndBrokerNameAndAccountHolderOrderByTransactionDate(
                        any(), any(), any(), any()))
                .thenReturn(lots);
        when(holdingPeriodService.classify(any(), any(), any(), any())).thenReturn(
                new HoldingPeriodResolution(CapitalGainsType.LONG_TERM, "EQUITY_LISTED", "held beyond 12 months"));

        // When
        portfolioService.sellStock(userMail, "sell-1", sell);

        // Then -- V1 still writes its outcome through its own path, and never through the new one
        verifyNoInteractions(tradeOutcomeRecorder);
        verify(tradeOutcomeService).saveTradeOutcome(eq(userMail), any());
    }
}
