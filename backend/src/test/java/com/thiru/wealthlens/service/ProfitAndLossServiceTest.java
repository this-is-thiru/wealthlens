package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.model.MonthlyChargeSummary;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.FinancialReportResponse;
import com.thiru.wealthlens.portfolio.dto.MonthlyReportResponse;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossResponse;
import com.thiru.wealthlens.portfolio.dto.ReportModelResponse;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitAndLossContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.FortnightReport;
import com.thiru.wealthlens.portfolio.entity.model.MonthlyReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.service.ChargeRecordingGateway;
import com.thiru.wealthlens.portfolio.service.LotChargeAllocator;
import com.thiru.wealthlens.portfolio.service.ProfitAndLossService;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.time.LocalDate;
import java.time.Month;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfitAndLossServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String STOCK_CODE = "RELIANCE";
    private static final BrokerName BROKER = BrokerName.ZERODHA;
    private static final String EXCHANGE = "NSE";
    private static final String ACCOUNT_HOLDER = "main";

    @Mock
    private ProfitAndLossRepository profitAndLossRepository;

    @Mock
    private ChargeRecordingGateway chargeRecordingGateway;

    private ProfitAndLossService profitAndLossService;

    @BeforeEach
    void setUp() {
        profitAndLossService = new ProfitAndLossService(profitAndLossRepository, chargeRecordingGateway);
    }

    // ========================================
    // updateProfitAndLoss with ProfitLossContext (v2)
    // ========================================

    @Test
    void updateProfitAndLoss_buyNonEquity_savesThePeriodWithNoRealisedProfit() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2024, 1, 15);
        ProfitLossContext context = new ProfitLossContext(
                "txn-124", 10.0, buyDate, 100.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.MUTUAL_FUND, TransactionType.BUY, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of()
        );

        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2023-2024")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        assertEquals(TEST_EMAIL, savedEntity.getEmail());
        assertEquals("2023-2024", savedEntity.getFinancialYear());
        // No realized profits set for BUY transactions
        assertNull(savedEntity.getRealisedProfits());
    }

    @Test
    void updateProfitAndLoss_sellMultipleLots_allRecorded() {
        // Given: Sell spanning multiple buy lots
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate1 = LocalDate.of(2023, 1, 15);
        LocalDate buyDate2 = LocalDate.of(2023, 2, 15);
        LocalDate sellDate = LocalDate.of(2023, 12, 15); // short term for both

        BuyContext buyContext1 = new BuyContext(5.0, buyDate1, 100.0, Optional.empty());
        BuyContext buyContext2 = new BuyContext(5.0, buyDate2, 120.0, Optional.empty());

        ProfitLossContext context = new ProfitLossContext(
                "txn-sell-3", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(buyContext1, buyContext2)
        );

        // Dec 15, 2023 -> FY 2023-2024
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2023-2024")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.recordSale(userMail, context, Optional.empty(), rowsOf(context));

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        assertNotNull(savedEntity.getRealisedProfits());
        assertNotNull(splitOf(savedEntity.getRealisedProfits(), AssetType.EQUITY, TradeSegment.DELIVERY));

        // Purchase and sell amounts should be aggregated from both lots
        // Lot1: 5 * 100 = 500 purchase, Lot2: 5 * 120 = 600 purchase, total = 1100
        // Sell: 10 * 150 = 1500
    }

    @Test
    void updateProfitAndLoss_sellNoBrokerTemplate_stillSavesPnl() {
        // Given: Sell without broker charge service returning a template
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2023, 1, 15);
        LocalDate sellDate = LocalDate.of(2023, 12, 15);
        BuyContext buyContext = new BuyContext(10.0, buyDate, 100.0, Optional.empty());

        ProfitLossContext context = new ProfitLossContext(
                "txn-sell-4", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(buyContext)
        );

        // Dec 15, 2023 -> FY 2023-2024
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2023-2024")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.recordSale(userMail, context, Optional.empty(), rowsOf(context));

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        // P&L should still be saved even without broker template
        assertNotNull(savedEntity.getRealisedProfits());
        assertNotNull(splitOf(savedEntity.getRealisedProfits(), AssetType.EQUITY, TradeSegment.DELIVERY));
    }

    @Test
    void updateProfitAndLoss_buyNoBrokerTemplate_stillSavesPnl() {
        // Given: Buy without broker charge service returning a template
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2024, 1, 15);
        ProfitLossContext context = new ProfitLossContext(
                "txn-125", 10.0, buyDate, 100.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.BUY, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of()
        );

        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2023-2024")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        // Entity should still be saved, just without broker charges
        assertNotNull(savedEntity);
        assertEquals(TEST_EMAIL, savedEntity.getEmail());
    }

    @Test
    void updateProfitAndLoss_invalidTransactionType_logsError() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate date = LocalDate.of(2024, 1, 15);
        ProfitLossContext context = new ProfitLossContext(
                "txn-invalid", 10.0, date, 100.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, null, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of()
        );

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then: should not save anything for invalid transaction type
        verify(profitAndLossRepository, never()).save(any());
    }

    @Test
    void updateProfitAndLoss_corporateActionSell_skipped() {
        // Given: Sell with a corporate action type should be silently skipped
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2023, 1, 15);
        LocalDate sellDate = LocalDate.of(2023, 12, 15);
        BuyContext buyContext = new BuyContext(10.0, buyDate, 100.0, Optional.empty());

        ProfitLossContext context = new ProfitLossContext(
                "txn-sell-ca", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.SELL, CorporateActionType.BONUS, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(buyContext)
        );

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then: should not save anything for corporate action sell
        verify(profitAndLossRepository, never()).save(any());
    }

    // ========================================
    // updateProfitAndLossWithAmcCharges
    // ========================================

    // ========================================
    // Phase B — shadow recording
    //
    // The engine sees every trade the live flow processes, and changes none of it. The pairing that
    // matters is this block against updateProfitAndLoss_buyNonEquity_skipsBrokerCharges above: the
    // superseded implementation still skips a mutual fund, and the engine still sees it.
    // ========================================

    @Test
    void updateProfitAndLoss_buyEquity_handsTheTradeToTheChargeEngine() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2024, 1, 15);
        ProfitLossContext context = new ProfitLossContext(
                "txn-shadow-buy", 10.0, buyDate, 100.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.BUY, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of()
        );

        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2023-2024")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        verify(chargeRecordingGateway).record(userMail, context);
    }

    @Test
    void updateProfitAndLoss_sellEquity_handsTheTradeToTheChargeEngine() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate sellDate = LocalDate.of(2024, 6, 15);
        ProfitLossContext context = new ProfitLossContext(
                "txn-shadow-sell", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(new BuyContext(10.0, LocalDate.of(2024, 1, 15), 100.0, Optional.empty()))
        );

        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2024-2025")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.recordSale(userMail, context, Optional.empty(), rowsOf(context));

        // Then
        verify(chargeRecordingGateway).record(userMail, context);
    }

    /**
     * FR-8. The {@code assetType == EQUITY} gate guards the superseded implementation only; the
     * engine is handed every asset type, which is the whole reason Phase B produces data worth
     * reconciling for mutual funds and bonds.
     */
    @Test
    void updateProfitAndLoss_buyNonEquity_stillHandsTheTradeToTheChargeEngine() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2024, 1, 15);
        ProfitLossContext context = new ProfitLossContext(
                "txn-shadow-mf", 10.0, buyDate, 100.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.MUTUAL_FUND, TransactionType.BUY, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of()
        );

        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2023-2024")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        verify(chargeRecordingGateway).record(userMail, context);
    }

    /**
     * The property the whole phase rests on: what the engine returns reaches nothing. The gateway
     * answers with a total and the saved P&L carries no trace of it.
     */
    @Test
    void updateProfitAndLoss_whenTheEngineComputesACharge_theSavedPnlIsUnchanged() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate sellDate = LocalDate.of(2024, 6, 15);
        ProfitLossContext context = new ProfitLossContext(
                "txn-shadow-ignored", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(new BuyContext(10.0, LocalDate.of(2024, 1, 15), 100.0, Optional.empty()))
        );

        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.of(
                new ChargeComputation("sched-1", "ZERODHA_EQ_DELIVERY_2025_04", null, null, null,
                        ChargeResolution.RESOLVED, List.of(), 999.99)));
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2024-2025")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.recordSale(userMail, context, Optional.empty(), rowsOf(context));

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        // The figures are the trade's own -- 10 bought at 100, sold at 150 -- and the engine's
        // 999.99 reached no bucket at all. (getProfit() is 0 here because the yearly report
        // accumulates purchase and sell amounts only; that is pre-existing behaviour, not a
        // consequence of shadow recording.)
        FinancialReport stcg = splitOf(savedEntity.getRealisedProfits(), AssetType.EQUITY, TradeSegment.DELIVERY);
        assertEquals(1000.0, stcg.getPurchaseAmount());
        assertEquals(1500.0, stcg.getSellAmount());
        assertEquals(0.0, stcg.getBrokerage());
    }

    /**
     * A corporate-action sell is not processed by the live flow, so it is not shadow-recorded
     * either. Recording a charge for an event the P&L ignores would put a row in the reconciliation
     * report with nothing to reconcile it against.
     */
    @Test
    void updateProfitAndLoss_corporateActionSell_isNotHandedToTheChargeEngine() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        ProfitLossContext context = new ProfitLossContext(
                "txn-shadow-ca", 10.0, LocalDate.of(2024, 6, 15), 150.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.SELL, CorporateActionType.BONUS, AccountType.SELF,
                ACCOUNT_HOLDER, List.of()
        );

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        verify(chargeRecordingGateway, never()).record(any(), any());
    }

    // ========================================
    // Chunk 10b part 2 — the computed charge reaches realised P&L
    //
    // Written only when a computation is *passed in*, which is the V2 flow. V1 reaches the two-arg
    // overload, prices its trade through the gateway as before, and writes no summary — its
    // behaviour is untouched.
    // ========================================

    private static ChargeComputation computedWith(double brokerage, double stt, double gst) {
        List<ChargeLine> lines = List.of(line("BROKERAGE", brokerage), line("STT", stt), line("GST", gst));
        return new ChargeComputation("sched-1", "ZERODHA_EQ_DELIVERY_2025_04", null, null, null,
                ChargeResolution.RESOLVED, lines, brokerage + stt + gst);
    }

    private static ChargeLine line(String code, double amount) {
        ChargeLine chargeLine = new ChargeLine();
        chargeLine.setCode(code);
        chargeLine.setAmount(amount);
        return chargeLine;
    }

    private ProfitAndLossEntity savedPnl() {
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());
        return captor.getValue();
    }

    private void expectNoExistingPnl(String financialYear) {
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq(financialYear)))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ProfitLossContext buyOn(LocalDate date, AssetType assetType, AccountType accountType) {
        return new ProfitLossContext("txn-sum", 10.0, date, 100.0, STOCK_CODE, BROKER, EXCHANGE,
                assetType, TransactionType.BUY, null, accountType, ACCOUNT_HOLDER, List.of());
    }

    @Test
    void updateProfitAndLoss_whenAComputationIsPassedIn_mergesItIntoTheChargeSummary() {
        // Given
        expectNoExistingPnl("2025-2026");
        ProfitLossContext context = buyOn(LocalDate.of(2025, 6, 10), AssetType.EQUITY, AccountType.SELF);

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context,
                Optional.of(computedWith(20.00, 100.00, 3.60)));

        // Then
        YearlyChargeSummary summary = savedPnl().getRealisedProfits().getYearlyChargeSummary();
        assertNotNull(summary);
        assertEquals(123.60, summary.getTotalCharges(), 0.001);
        assertEquals(20.00, summary.getAmountByCode().get("BROKERAGE"), 0.001);
        assertEquals(100.00, summary.getAmountByCode().get("STT"), 0.001);
    }

    /** The month and the fortnight the trade fell in, mirroring the report the old path draws. */
    @Test
    void updateProfitAndLoss_mergesIntoTheMonthAndTheFortnightTheTradeFellIn() {
        // Given — the 10th, so the first half of June
        expectNoExistingPnl("2025-2026");
        ProfitLossContext context = buyOn(LocalDate.of(2025, 6, 10), AssetType.EQUITY, AccountType.SELF);

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context,
                Optional.of(computedWith(20.00, 100.00, 3.60)));

        // Then
        MonthlyChargeSummary june = savedPnl().getRealisedProfits()
                .getYearlyChargeSummary().getMonthlyReport().get(Month.JUNE);
        assertNotNull(june);
        assertEquals(123.60, june.getTotalCharges(), 0.001);
        assertEquals(123.60, june.getFirstHalfCharges().getTotalCharges(), 0.001);
        // A fortnight with no trades reads as having none, not as having charged zero.
        assertNull(june.getSecondHalfCharges());
    }

    /** Mirrors the old report exactly: anything that is not SELF lands in the out-sourced bucket. */
    @Test
    void updateProfitAndLoss_whenTheAccountIsNotSelf_mergesIntoTheOutSourcedSummary() {
        // Given
        expectNoExistingPnl("2025-2026");
        ProfitLossContext context = buyOn(LocalDate.of(2025, 6, 10), AssetType.EQUITY, AccountType.OUTSOURCED);

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context,
                Optional.of(computedWith(20.00, 100.00, 3.60)));

        // Then
        ProfitAndLossEntity saved = savedPnl();
        assertNotNull(saved.getOutSourcedRealisedProfits().getYearlyChargeSummary());
        assertNull(saved.getRealisedProfits());
    }

    /** FR-8 again: the summary has no asset-type gate, so a mutual fund reaches it. */
    @Test
    void updateProfitAndLoss_whenAssetTypeIsNotEquity_stillMergesIntoTheChargeSummary() {
        // Given
        expectNoExistingPnl("2025-2026");
        ProfitLossContext context = buyOn(LocalDate.of(2025, 6, 10), AssetType.MUTUAL_FUND, AccountType.SELF);

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context,
                Optional.of(computedWith(0.0, 0.0, 2.00)));

        // Then
        assertNotNull(savedPnl().getRealisedProfits().getYearlyChargeSummary());
    }

    /**
     * The V1 shape. Nothing is passed in, so the trade is priced through the gateway exactly as
     * before and no summary is written — V1's behaviour is unchanged by this chunk.
     */
    @Test
    void updateProfitAndLoss_whenNoComputationIsPassedIn_writesNoChargeSummary() {
        // Given
        expectNoExistingPnl("2025-2026");
        ProfitLossContext context = buyOn(LocalDate.of(2025, 6, 10), AssetType.EQUITY, AccountType.SELF);

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context);

        // Then
        assertNull(savedPnl().getRealisedProfits());
        verify(chargeRecordingGateway).record(any(), any());
    }

    /** The engine declined — no card, or the kill switch. There is nothing to merge. */
    @Test
    void updateProfitAndLoss_whenTheComputationIsEmpty_writesNoChargeSummary() {
        // Given
        expectNoExistingPnl("2025-2026");
        ProfitLossContext context = buyOn(LocalDate.of(2025, 6, 10), AssetType.EQUITY, AccountType.SELF);

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context, Optional.empty());

        // Then
        assertNull(savedPnl().getRealisedProfits());
    }

    /** Two trades in one period accumulate rather than replace. */
    @Test
    void updateProfitAndLoss_asecondTradeInThePeriodAccumulates() {
        // Given — the repository has to behave like one: the second read returns what the first
        // write saved, or there is nothing to accumulate onto and the test proves nothing.
        AtomicReference<ProfitAndLossEntity> stored = new AtomicReference<>();
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2025-2026")))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return invocation.getArgument(0);
                });
        ProfitLossContext context = buyOn(LocalDate.of(2025, 6, 10), AssetType.EQUITY, AccountType.SELF);

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context,
                Optional.of(computedWith(20.00, 100.00, 3.60)));
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context,
                Optional.of(computedWith(20.00, 100.00, 3.60)));

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository, times(2)).save(captor.capture());
        YearlyChargeSummary summary = captor.getAllValues().getLast().getRealisedProfits().getYearlyChargeSummary();
        assertEquals(247.20, summary.getTotalCharges(), 0.001);
        assertEquals(40.00, summary.getAmountByCode().get("BROKERAGE"), 0.001);
    }

    /**
     * Cut 1. The superseded implementation is no longer called from the trade path at all — by V1
     * buy, which reached it through the two-arg overload, or by V2.
     *
     * <p>Safe because it never did anything: it calls {@code addUserBrokerChargeEntry}, which
     * returns null when no {@code broker_charges} template exists for the broker and date, and
     * production holds zero {@code yearly_broker_charges} documents — the only thing that block
     * could have written. It logged an error per trade and produced nothing.
     */
    @Test
    void updateProfitAndLoss_neverCallsTheSupersededImplementation() {
        // Given
        expectNoExistingPnl("2025-2026");
        ProfitLossContext equityBuy = buyOn(LocalDate.of(2025, 6, 10), AssetType.EQUITY, AccountType.SELF);

        // When — the V1 shape: nothing passed in
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), equityBuy);

        // Then
    }

    @Test
    void updateProfitAndLoss_sellNeverCallsTheSupersededImplementation() {
        // Given
        expectNoExistingPnl("2025-2026");
        ProfitLossContext sell = new ProfitLossContext("txn-cut1", 10.0, LocalDate.of(2025, 6, 15), 150.0,
                STOCK_CODE, BROKER, EXCHANGE, AssetType.EQUITY, TransactionType.SELL, null,
                AccountType.SELF, ACCOUNT_HOLDER, List.of(new BuyContext(10.0, LocalDate.of(2025, 1, 5), 100.0, Optional.empty())));

        // When
        profitAndLossService.recordSale(UserMail.from(TEST_EMAIL), sell, Optional.empty(), rowsOf(sell));

        // Then
    }

    // ========================================
    // Helpers for a V2 sale
    // ========================================

    private ProfitAndLossEntity sellAndCapture(ProfitLossContext context, String financialYear) {
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq(financialYear)))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        profitAndLossService.recordSale(UserMail.from(TEST_EMAIL), context, Optional.empty(), rowsOf(context));

        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ProfitLossContext sell(AssetType assetType, TradeSegment segment,
                                          LocalDate buyDate, LocalDate sellDate) {
        return new ProfitLossContext(
                "txn-d8", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE, assetType,
                TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(new BuyContext(10.0, buyDate, 100.0, Optional.empty())), segment);
    }

    // ========================================
    // D-366 -- the split by asset type and segment
    // ========================================

    /**
     * The rows {@code TradeOutcomeRecorder} would save for {@code context}'s lots, with no charges, so each lot's net
     * profit is its price difference times its quantity.
     */
    private static List<TradeOutcomeContext> rowsOf(ProfitLossContext context) {
        return context.buyContexts().stream()
                .map(lot -> row(lot.price(), context.price(), lot.quantity(), 0, 0, 0, 0))
                .toList();
    }

    private static TradeOutcomeContext row(double buyPrice, double sellPrice, double quantity, double buyCharges,
                                           double buyMisc, double sellCharges, double sellMisc) {
        double totalBuy = buyPrice * quantity + buyCharges + buyMisc;
        double totalSell = sellPrice * quantity - sellCharges - sellMisc;
        return TradeOutcomeContext.builder().corporateActionAdjustedBuyPrice(buyPrice).buyQuantity(quantity)
                .sellPrice(sellPrice).sellQuantity(quantity).buyBrokerCharges(buyCharges).buyMiscCharges(buyMisc)
                .sellBrokerCharges(sellCharges).sellMiscCharges(sellMisc).netProfit(totalSell - totalBuy).build();
    }

    private void sellThrough(ProfitLossContext context) {
        profitAndLossService.recordSale(UserMail.from(TEST_EMAIL), context, Optional.empty(), rowsOf(context));
    }

    @Test
    void recordSale_whenTheRowsCarryCharges_recordsProfitBrokerageAndMisc() {
        // Given -- lot 1: 10 bought at 100 (buy charge 20, misc 5), sold at 150 with sell charges 30;
        // lot 2: 5 bought at 120 (charge 4, misc 1), sold at 150 with sell charges 15
        ProfitLossContext context = sellLots(AssetType.EQUITY, AccountType.SELF, List.of(
                new BuyContext(10.0, LocalDate.of(2023, 1, 15), 100.0, Optional.empty()),
                new BuyContext(5.0, LocalDate.of(2023, 6, 15), 120.0, Optional.empty())));
        List<TradeOutcomeContext> rows = List.of(row(100, 150, 10, 20, 5, 30, 0), row(120, 150, 5, 4, 1, 15, 0));
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024")).thenReturn(Optional.empty());

        // When
        profitAndLossService.recordSale(UserMail.from(TEST_EMAIL), context, Optional.empty(), rows);

        // Then -- purchase 1,000 + 600 = 1,600; sell 1,500 + 750 = 2,250; brokerage 20 + 30 + 4 + 15 = 69;
        // misc 5 + 1 = 6; profit (1,500 - 30) - (1,000 + 25) = 445 and (750 - 15) - (600 + 5) = 130, so 575
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());
        FinancialReport year = splitOf(captor.getValue().getRealisedProfits(), AssetType.EQUITY, TradeSegment.DELIVERY);
        MoneyAssert.assertMoney(1600.0, year.getPurchaseAmount());
        MoneyAssert.assertMoney(2250.0, year.getSellAmount());
        MoneyAssert.assertMoney(69.0, year.getBrokerage());
        MoneyAssert.assertMoney(6.0, year.getMiscCharges());
        MoneyAssert.assertMoney(575.0, year.getProfit());
        MoneyAssert.assertMoney(575.0, year.getMonthlyReport().get(Month.DECEMBER).getProfit());
    }

    @Test
    void updateProfitAndLoss_whenASaleIsSentWithoutItsRows_isRefused() {
        // Given
        ProfitLossContext context = sellLots(AssetType.EQUITY, AccountType.SELF,
                List.of(new BuyContext(10.0, LocalDate.of(2023, 1, 15), 100.0, Optional.empty())));

        // When / Then -- a sale recorded without its rows would have no profit, brokerage or misc (W-169)
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context));
        assertTrue(refused.getMessage().contains("recordSale"));
        verify(profitAndLossRepository, never()).save(any());
    }

    @Test
    void recordSale_whenTheRowsDoNotMatchTheLots_isRefused() {
        // Given -- two lots, one row
        ProfitLossContext context = sellLots(AssetType.EQUITY, AccountType.SELF, List.of(
                new BuyContext(10.0, LocalDate.of(2023, 1, 15), 100.0, Optional.empty()),
                new BuyContext(5.0, LocalDate.of(2023, 6, 15), 120.0, Optional.empty())));
        List<TradeOutcomeContext> oneRow = List.of(row(100, 150, 10, 0, 0, 0, 0));

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> profitAndLossService.recordSale(UserMail.from(TEST_EMAIL), context, Optional.empty(), oneRow));
        verify(profitAndLossRepository, never()).save(any());
    }

    private static FinancialReport splitOf(RealisedProfits realisedProfits, AssetType assetType, TradeSegment segment) {
        return realisedProfits.getGainsByAssetAndSegment().get(assetType).get(segment);
    }

    /** A V2 sale of {@code lots} at Rs.150 on 2023-12-15, by the given account. */
    private static ProfitLossContext sellLots(AssetType assetType, AccountType accountType, List<BuyContext> lots) {
        double quantity = lots.stream().mapToDouble(BuyContext::quantity).sum();
        return new ProfitLossContext("txn-split", quantity, LocalDate.of(2023, 12, 15), 150.0, STOCK_CODE, BROKER,
                EXCHANGE, assetType, TransactionType.SELL, null, accountType, ACCOUNT_HOLDER, lots,
                TradeSegment.DELIVERY);
    }

    /** A V1 sale: 10 of a lot bought at Rs.100 on 2022-06-15, sold at Rs.150 on 2023-12-15. */
    private static ProfitAndLossContext v1Sale(AssetType soldAs, AssetType lotAs, TradeSegment segment) {
        AssetEntity lot = new AssetEntity();
        lot.setPrice(100.0);
        lot.setQuantity(10.0);
        lot.setTransactionDate(LocalDate.of(2022, 6, 15));
        lot.setAssetType(lotAs);
        AssetRequest sell = new AssetRequest();
        sell.setStockCode(STOCK_CODE);
        sell.setPrice(150.0);
        sell.setQuantity(10.0);
        sell.setBrokerCharges(30.0);
        sell.setTransactionDate(LocalDate.of(2023, 12, 15));
        sell.setAssetType(soldAs);
        sell.setSegment(segment);
        sell.setAccountType(AccountType.SELF);
        return ProfitAndLossContext.from(lot, sell, 10.0, 20.0, 5.0);
    }

    private ProfitAndLossEntity v1SellAndCapture(ProfitAndLossContext context) {
        ProfitAndLossEntity entity = new ProfitAndLossEntity(TEST_EMAIL, "2023-2024");
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.of(entity));
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context);
        return entity;
    }

    @Test
    void updateProfitAndLoss_whenV2SellsDelivery_recordsTheSplitBesideThePair() {
        // Given -- 10 bought at 100, sold at 150: purchase 1,000, sell 1,500
        ProfitLossContext context = sell(AssetType.EQUITY, TradeSegment.DELIVERY,
                LocalDate.of(2023, 1, 15), LocalDate.of(2023, 12, 15));

        // When
        RealisedProfits saved = sellAndCapture(context, "2023-2024").getRealisedProfits();

        // Then
        FinancialReport split = splitOf(saved, AssetType.EQUITY, TradeSegment.DELIVERY);
        MoneyAssert.assertMoney(1000.0, split.getPurchaseAmount());
        MoneyAssert.assertMoney(1500.0, split.getSellAmount());
    }

    @Test
    void updateProfitAndLoss_whenV2SellsIntraday_recordsItUnderIntraday() {
        // Given
        LocalDate sameDay = LocalDate.of(2023, 12, 15);
        ProfitLossContext context = sell(AssetType.EQUITY, TradeSegment.INTRADAY, sameDay, sameDay);

        // When
        RealisedProfits saved = sellAndCapture(context, "2023-2024").getRealisedProfits();

        // Then
        assertEquals(Set.of(TradeSegment.INTRADAY), saved.getGainsByAssetAndSegment().get(AssetType.EQUITY).keySet());
    }

    @Test
    void updateProfitAndLoss_whenV2LotsWereHeldForDifferentPeriods_recordsOneEntrySummingBoth() {
        // Given -- 5 at 100 bought 2022-01-15 (over a year) and 5 at 120 bought 2023-06-15 (under), both sold at 150
        ProfitLossContext context = sellLots(AssetType.EQUITY, AccountType.SELF, List.of(
                new BuyContext(5.0, LocalDate.of(2022, 1, 15), 100.0, Optional.empty()),
                new BuyContext(5.0, LocalDate.of(2023, 6, 15), 120.0, Optional.empty())));

        // When
        RealisedProfits saved = sellAndCapture(context, "2023-2024").getRealisedProfits();

        // Then -- purchase 500 + 600 = 1,100, sell 750 + 750 = 1,500, in one entry
        FinancialReport split = splitOf(saved, AssetType.EQUITY, TradeSegment.DELIVERY);
        MoneyAssert.assertMoney(1100.0, split.getPurchaseAmount());
        MoneyAssert.assertMoney(1500.0, split.getSellAmount());
    }

    @Test
    void updateProfitAndLoss_whenV2SellsTwoAssetTypes_recordsEachUnderItsOwn() {
        // Given
        ProfitAndLossEntity accumulating = new ProfitAndLossEntity(TEST_EMAIL, "2023-2024");
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.of(accumulating));
        List<BuyContext> lot = List.of(new BuyContext(10.0, LocalDate.of(2023, 1, 15), 100.0, Optional.empty()));

        // When
        sellThrough(
                sellLots(AssetType.EQUITY, AccountType.SELF, lot));
        sellThrough(
                sellLots(AssetType.MUTUAL_FUND, AccountType.SELF, lot));

        // Then
        assertEquals(Set.of(AssetType.EQUITY, AssetType.MUTUAL_FUND),
                accumulating.getRealisedProfits().getGainsByAssetAndSegment().keySet());
    }

    @Test
    void updateProfitAndLoss_whenV1Sells_recordsItsFiguresInTheSplit() {
        // When
        RealisedProfits saved = v1SellAndCapture(v1Sale(AssetType.EQUITY, AssetType.EQUITY, TradeSegment.DELIVERY))
                .getRealisedProfits();

        // Then -- purchase 1,000, sell 1,500, brokerage 20 + 30 = 50, misc 5, profit 500 - 50 - 5 = 445
        FinancialReport split = splitOf(saved, AssetType.EQUITY, TradeSegment.DELIVERY);
        MoneyAssert.assertMoney(1000.0, split.getPurchaseAmount());
        MoneyAssert.assertMoney(1500.0, split.getSellAmount());
        MoneyAssert.assertMoney(50.0, split.getBrokerage());
        MoneyAssert.assertMoney(5.0, split.getMiscCharges());
        MoneyAssert.assertMoney(445.0, split.getProfit());
    }

    @Test
    void updateProfitAndLoss_whenV1SaleHasNoSegment_recordsItUnderDelivery() {
        // When -- the entry default, D-295
        RealisedProfits saved = v1SellAndCapture(v1Sale(AssetType.EQUITY, AssetType.EQUITY, null))
                .getRealisedProfits();

        // Then
        assertEquals(Set.of(TradeSegment.DELIVERY), saved.getGainsByAssetAndSegment().get(AssetType.EQUITY).keySet());
    }

    @Test
    void updateProfitAndLoss_whenTheAccountIsOutsourced_recordsTheSplitThere() {
        // Given
        ProfitLossContext context = sellLots(AssetType.EQUITY, AccountType.OUTSOURCED,
                List.of(new BuyContext(10.0, LocalDate.of(2023, 1, 15), 100.0, Optional.empty())));

        // When
        ProfitAndLossEntity saved = sellAndCapture(context, "2023-2024");

        // Then
        assertNull(saved.getRealisedProfits());
        MoneyAssert.assertMoney(1500.0,
                splitOf(saved.getOutSourcedRealisedProfits(), AssetType.EQUITY, TradeSegment.DELIVERY).getSellAmount());
    }

    @Test
    void getProfitAndLoss_carriesTheSplitThroughToTheResponse() {
        // Given
        ProfitAndLossEntity entity = new ProfitAndLossEntity(TEST_EMAIL, "2023-2024");
        RealisedProfits realisedProfits = RealisedProfits.empty();
        FinancialReport intraday = FinancialReport.empty();
        intraday.setSellAmount(1500.0);
        realisedProfits.setGainsByAssetAndSegment(new EnumMap<>(Map.of(AssetType.EQUITY,
                new EnumMap<>(Map.of(TradeSegment.INTRADAY, intraday)))));
        entity.setRealisedProfits(realisedProfits);
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.of(entity));

        // When
        ProfitAndLossResponse response = profitAndLossService.getProfitAndLoss(
                UserMail.from(TEST_EMAIL), "2023-2024");

        // Then
        FinancialReportResponse mapped = response.getRealisedProfits().getGainsByAssetAndSegment()
                .get(AssetType.EQUITY).get(TradeSegment.INTRADAY);
        assertNotNull(mapped, "the split must survive the response copy");
        MoneyAssert.assertMoney(1500.0, mapped.getSellAmount());
    }

    @Test
    void updateProfitAndLoss_whenTheV2SaleHasNoAssetType_usesTheLots() {
        // Given
        ProfitLossContext context = sellLots(null, AccountType.SELF, List.of(
                new BuyContext(10.0, LocalDate.of(2023, 1, 15), 100.0, Optional.of(AssetType.EQUITY))));

        // When
        RealisedProfits saved = sellAndCapture(context, "2023-2024").getRealisedProfits();

        // Then
        assertEquals(Set.of(AssetType.EQUITY), saved.getGainsByAssetAndSegment().keySet());
    }

    @Test
    void updateProfitAndLoss_whenTheV1SaleHasNoAssetType_usesTheLots() {
        // When
        RealisedProfits saved = v1SellAndCapture(v1Sale(null, AssetType.EQUITY, TradeSegment.DELIVERY))
                .getRealisedProfits();

        // Then
        assertEquals(Set.of(AssetType.EQUITY), saved.getGainsByAssetAndSegment().keySet());
    }

    @Test
    void updateProfitAndLoss_whenNeitherTheSaleNorTheLotHasAnAssetType_isRefusedAndSavesNothing() {
        // Given
        ProfitLossContext context = sellLots(null, AccountType.SELF,
                List.of(new BuyContext(10.0, LocalDate.of(2023, 1, 15), 100.0, Optional.empty())));
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.empty());

        // When / Then
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> sellThrough(context));
        assertTrue(refused.getMessage().contains("Neither the sale of RELIANCE"));
        verify(profitAndLossRepository, never()).save(any());
    }

    @Test
    void getProfitAndLoss_reportsTheRealAmountsAtEveryLevelOfTheHierarchy() {
        // Given -- a period with amounts on the year, the month and the fortnight.
        // ReportModelResponse names its fields purchasePrice / sellPrice / brokerCharges while
        // ReportModel names them purchaseAmount / sellAmount / brokerage, and safeCopy maps by
        // name -- so every one of those read 0.0 in the response, at all three levels, for both
        // realised and out-sourced profits. Only profit and miscCharges ever came through.
        FortnightReport fortnight = FortnightReport.from();
        fortnight.setPurchaseAmount(100.0);
        fortnight.setSellAmount(150.0);
        fortnight.setBrokerage(3.0);

        MonthlyReport monthly = new MonthlyReport(Month.DECEMBER);
        monthly.setPurchaseAmount(200.0);
        monthly.setSellAmount(300.0);
        monthly.setBrokerage(6.0);
        monthly.setFirstFortnightReport(fortnight);

        FinancialReport yearly = FinancialReport.empty();
        yearly.setPurchaseAmount(1000.0);
        yearly.setSellAmount(1500.0);
        yearly.setBrokerage(30.0);
        yearly.setMonthlyReport(new java.util.HashMap<>(Map.of(Month.DECEMBER, monthly)));

        RealisedProfits realisedProfits = RealisedProfits.empty();
        realisedProfits.setGainsByAssetAndSegment(new EnumMap<>(Map.of(AssetType.EQUITY,
                new EnumMap<>(Map.of(TradeSegment.DELIVERY, yearly)))));
        ProfitAndLossEntity entity = new ProfitAndLossEntity(TEST_EMAIL, "2023-2024");
        entity.setRealisedProfits(realisedProfits);
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.of(entity));

        // When
        ProfitAndLossResponse response = profitAndLossService.getProfitAndLoss(
                UserMail.from(TEST_EMAIL), "2023-2024");

        // Then
        FinancialReportResponse year = response.getRealisedProfits().getGainsByAssetAndSegment().get(AssetType.EQUITY)
                .get(TradeSegment.DELIVERY);
        MoneyAssert.assertMoney("yearly purchase", 1000.0, year.getPurchaseAmount());
        MoneyAssert.assertMoney("yearly sell", 1500.0, year.getSellAmount());
        MoneyAssert.assertMoney("yearly brokerage", 30.0, year.getBrokerage());

        MonthlyReportResponse month = year.getMonthlyReport().get(Month.DECEMBER);
        MoneyAssert.assertMoney("monthly purchase", 200.0, month.getPurchaseAmount());
        MoneyAssert.assertMoney("monthly sell", 300.0, month.getSellAmount());
        MoneyAssert.assertMoney("monthly brokerage", 6.0, month.getBrokerage());

        ReportModelResponse half = month.getFirstFortnightReport();
        MoneyAssert.assertMoney("fortnight purchase", 100.0, half.getPurchaseAmount());
        MoneyAssert.assertMoney("fortnight sell", 150.0, half.getSellAmount());
        MoneyAssert.assertMoney("fortnight brokerage", 3.0, half.getBrokerage());
    }

    @Test
    void reportModelResponse_emitsTheSameNamesTheEntityUses() {
        // Given -- the names must stay identical to ReportModel's. safeCopy maps by name, so the
        // moment they diverge again every amount in this response silently reads 0.0.
        ReportModelResponse response = new ReportModelResponse();
        response.setPurchaseAmount(1000.0);
        response.setSellAmount(1500.0);
        response.setBrokerage(30.0);

        // When
        String json = TJsonMapper.writeValueAsString(response);

        // Then
        assertTrue(json.contains("\"purchaseAmount\""), json);
        assertTrue(json.contains("\"sellAmount\""), json);
        assertTrue(json.contains("\"brokerage\""), json);
        assertFalse(json.contains("purchasePrice"), "the old name must be gone, not aliased");
        assertFalse(json.contains("sellPrice"), "the old name must be gone, not aliased");
        assertFalse(json.contains("brokerCharges"), "the old name must be gone, not aliased");
    }

    @Test
    void updateProfitAndLoss_sellOnThirtyFirstMarch_landsInTheYearThatIsEnding() {
        // Given -- FY 2023-24 runs to 31 March 2024 inclusive. A sale that day was being filed
        // into 2024-2025. One day wrong every year, and 31 March is a heavy day for tax-loss
        // harvesting, so it is the single most likely date to hit it.
        ProfitLossContext context = sell(AssetType.EQUITY, TradeSegment.DELIVERY,
                LocalDate.of(2023, 6, 15), LocalDate.of(2024, 3, 31));

        // When
        ProfitAndLossEntity saved = sellAndCapture(context, "2023-2024");

        // Then
        assertEquals("2023-2024", saved.getFinancialYear());
    }

    @Test
    void updateProfitAndLoss_accumulatingManySells_keepsTheStoredTotalExact() {
        // Given -- ten sells of Rs.0.10 into one period. Raw `+=` gives 0.9999999999999999, which
        // prints as 1.00 and fails every equality check and every exact Mongo query (TL-8).
        ProfitAndLossEntity accumulating = new ProfitAndLossEntity(TEST_EMAIL, "2023-2024");
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.of(accumulating));
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        for (int i = 0; i < 10; i++) {
            ProfitLossContext context = new ProfitLossContext(
                    "txn-" + i, 1.0, LocalDate.of(2023, 12, 15), 0.10, STOCK_CODE, BROKER, EXCHANGE,
                    AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                    List.of(new BuyContext(1.0, LocalDate.of(2023, 6, 15), 0.05, Optional.empty())),
                    TradeSegment.DELIVERY);
            profitAndLossService.recordSale(UserMail.from(TEST_EMAIL), context, Optional.empty(), rowsOf(context));
        }

        // Then -- exact equality, deliberately
        FinancialReport yearly = splitOf(accumulating.getRealisedProfits(), AssetType.EQUITY, TradeSegment.DELIVERY);
        assertEquals(1.00, yearly.getSellAmount());
        assertEquals(0.50, yearly.getPurchaseAmount());
    }

    @Test
    void updateProfitAndLoss_v1_accumulatingManySells_keepsTheStoredTotalExact() {
        // Given -- the same ten Rs.0.10 sells, through the V1 overload (B-5). Canonicalising here
        // changes no logic: the same amounts fold into the same fields in the same order.
        ProfitAndLossEntity accumulating = new ProfitAndLossEntity(TEST_EMAIL, "2023-2024");
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.of(accumulating));
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssetEntity lot = new AssetEntity();
        lot.setPrice(0.05);
        lot.setQuantity(100.0);
        lot.setTransactionDate(LocalDate.of(2023, 6, 15));
        lot.setAssetType(AssetType.EQUITY);

        AssetRequest sell = new AssetRequest();
        sell.setPrice(0.10);
        sell.setQuantity(1.0);
        sell.setTransactionDate(LocalDate.of(2023, 12, 15));
        sell.setAssetType(AssetType.EQUITY);
        sell.setAccountType(AccountType.SELF);

        // When
        for (int i = 0; i < 10; i++) {
            profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL),
                    ProfitAndLossContext.from(lot, sell, 1.0, 0.0, 0.0));
        }

        // Then -- raw += gives 0.9999999999999999 here too
        FinancialReport yearly = splitOf(accumulating.getRealisedProfits(), AssetType.EQUITY, TradeSegment.DELIVERY);
        MoneyAssert.assertCanonical("V1 sell amount", 1.00, yearly.getSellAmount());
        MoneyAssert.assertCanonical("V1 purchase amount", 0.50, yearly.getPurchaseAmount());
    }

    @Test
    void updateProfitAndLoss_whenTheLotHasZeroQuantity_writesZeroChargeRatherThanInfinity() {
        // Given -- a charge divided by zero quantity. This used to produce Infinity, which was
        // accumulated into the period and stored; every later trade then added to Infinity and
        // stayed there. It never threw, so it read as handled.
        ProfitAndLossEntity accumulating = new ProfitAndLossEntity(TEST_EMAIL, "2024-2025");
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2024-2025"))
                .thenReturn(Optional.of(accumulating));
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssetEntity zeroQuantityLot = new AssetEntity();
        zeroQuantityLot.setPrice(100.0);
        zeroQuantityLot.setQuantity(0.0);
        zeroQuantityLot.setBrokerCharges(10.0);
        zeroQuantityLot.setMiscCharges(5.0);
        zeroQuantityLot.setTransactionDate(LocalDate.of(2024, 6, 15));
        zeroQuantityLot.setAssetType(AssetType.EQUITY);

        AssetRequest sell = new AssetRequest();
        sell.setPrice(150.0);
        sell.setQuantity(1.0);
        sell.setBrokerCharges(8.0);
        sell.setTransactionDate(LocalDate.of(2024, 12, 15));
        sell.setAssetType(AssetType.EQUITY);
        sell.setAccountType(AccountType.SELF);

        // When
        // The purchase-side share is now computed by LotChargeAllocator, which returns 0 for a
        // zero-quantity lot rather than dividing. The sell side still divides, and is still guarded.
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL),
                ProfitAndLossContext.from(zeroQuantityLot, sell, 1.0,
                        LotChargeAllocator.deductBroker(zeroQuantityLot, 1.0, zeroQuantityLot.getQuantity()),
                        LotChargeAllocator.deductMisc(zeroQuantityLot, 1.0, zeroQuantityLot.getQuantity())));

        // Then -- nothing stored may be non-finite, at any level of the hierarchy
        FinancialReport yearly = splitOf(accumulating.getRealisedProfits(), AssetType.EQUITY, TradeSegment.DELIVERY);
        assertTrue(Double.isFinite(yearly.getBrokerage()), "brokerage was " + yearly.getBrokerage());
        assertTrue(Double.isFinite(yearly.getMiscCharges()), "miscCharges was " + yearly.getMiscCharges());
        assertTrue(Double.isFinite(yearly.getProfit()), "profit was " + yearly.getProfit());
        MoneyAssert.assertCanonical("no charge can be spread over zero units", 8.0, yearly.getBrokerage());
    }
}
