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
import com.thiru.wealthlens.portfolio.dto.FinancialReportResponse;
import com.thiru.wealthlens.portfolio.dto.MonthlyReportResponse;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossResponse;
import com.thiru.wealthlens.portfolio.dto.ReportModelResponse;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.HoldingPeriodPolicyEntity;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.FortnightReport;
import com.thiru.wealthlens.portfolio.entity.model.MonthlyReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodResolver;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodService;
import com.thiru.wealthlens.portfolio.holding.TradeClassifier;
import com.thiru.wealthlens.portfolio.repository.HoldingPeriodPolicyRepository;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.service.ChargeRecordingGateway;
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
import org.springframework.core.io.ClassPathResource;

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

    @Mock
    private HoldingPeriodPolicyRepository holdingPeriodPolicyRepository;

    private ProfitAndLossService profitAndLossService;

    /**
     * A real classifier over the shipped policies rather than a mock. These tests assert which
     * bucket a gain lands in, and a stubbed classifier would make that assertion say only what the
     * stub was told to say.
     */
    @BeforeEach
    void setUp() throws Exception {
        String json = new String(new ClassPathResource(
                "data/holding-periods/holding-period-policies.json").getInputStream().readAllBytes());
        when(holdingPeriodPolicyRepository.findAll())
                .thenReturn(TJsonMapper.readAsList(json, HoldingPeriodPolicyEntity.class));
        HoldingPeriodService holdingPeriodService =
                new HoldingPeriodService(new HoldingPeriodResolver(holdingPeriodPolicyRepository));
        profitAndLossService = new ProfitAndLossService(
                profitAndLossRepository,
                holdingPeriodService,
                new TradeClassifier(holdingPeriodService),
                chargeRecordingGateway);
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
    void updateProfitAndLoss_sellShortTerm_updatesStcg() {
        // Given: Buy date more than 1 year before sell date -> Long term
        // For short term: sell within 1 year of buy
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2023, 1, 15);
        LocalDate sellDate = LocalDate.of(2023, 12, 15); // < 1 year = short term
        BuyContext buyContext = new BuyContext(10.0, buyDate, 100.0);

        ProfitLossContext context = new ProfitLossContext(
                "txn-sell-1", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(buyContext)
        );

        // Dec 15, 2023 -> FY 2023-2024 (after March 31, 2023)
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
        assertNotNull(savedEntity.getRealisedProfits());
        assertNotNull(savedEntity.getRealisedProfits().getShortTermCapitalGains());
        assertEquals("2023-2024", savedEntity.getFinancialYear());
    }

    @Test
    void updateProfitAndLoss_sellLongTerm_updatesLtcg() {
        // Given: Buy date more than 1 year before sell date -> Long term
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2022, 1, 15);
        LocalDate sellDate = LocalDate.of(2023, 6, 15); // > 1 year = long term
        BuyContext buyContext = new BuyContext(10.0, buyDate, 100.0);

        ProfitLossContext context = new ProfitLossContext(
                "txn-sell-2", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(buyContext)
        );

        // June 15, 2023 -> FY 2022-2023 (before April 1, 2023)
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2022-2023")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        assertNotNull(savedEntity.getRealisedProfits());
        assertNotNull(savedEntity.getRealisedProfits().getLongTermCapitalGains());
        // STCG should be empty/not set
        assertNull(savedEntity.getRealisedProfits().getShortTermCapitalGains());
    }

    @Test
    void updateProfitAndLoss_sellMultipleLots_allRecorded() {
        // Given: Sell spanning multiple buy lots
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate1 = LocalDate.of(2023, 1, 15);
        LocalDate buyDate2 = LocalDate.of(2023, 2, 15);
        LocalDate sellDate = LocalDate.of(2023, 12, 15); // short term for both

        BuyContext buyContext1 = new BuyContext(5.0, buyDate1, 100.0);
        BuyContext buyContext2 = new BuyContext(5.0, buyDate2, 120.0);

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
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        assertNotNull(savedEntity.getRealisedProfits());
        assertNotNull(savedEntity.getRealisedProfits().getShortTermCapitalGains());

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
        BuyContext buyContext = new BuyContext(10.0, buyDate, 100.0);

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
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        // P&L should still be saved even without broker template
        assertNotNull(savedEntity.getRealisedProfits());
        assertNotNull(savedEntity.getRealisedProfits().getShortTermCapitalGains());
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
        BuyContext buyContext = new BuyContext(10.0, buyDate, 100.0);

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
                List.of(new BuyContext(10.0, LocalDate.of(2024, 1, 15), 100.0))
        );

        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2024-2025")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

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
                List.of(new BuyContext(10.0, LocalDate.of(2024, 1, 15), 100.0))
        );

        when(chargeRecordingGateway.record(any(), any())).thenReturn(Optional.of(
                new ChargeComputation("sched-1", "ZERODHA_EQ_DELIVERY_2025_04", null,
                        ChargeResolution.RESOLVED, List.of(), 999.99)));
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2024-2025")))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        // The figures are the trade's own -- 10 bought at 100, sold at 150 -- and the engine's
        // 999.99 reached no bucket at all. (getProfit() is 0 here because the yearly report
        // accumulates purchase and sell amounts only; that is pre-existing behaviour, not a
        // consequence of shadow recording.)
        FinancialReport stcg = savedEntity.getRealisedProfits().getShortTermCapitalGains();
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
        return new ChargeComputation("sched-1", "ZERODHA_EQ_DELIVERY_2025_04", null,
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
                AccountType.SELF, ACCOUNT_HOLDER, List.of(new BuyContext(10.0, LocalDate.of(2025, 1, 5), 100.0)));

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), sell);

        // Then
    }

    // ========================================
    // D8 -- the classification map, beside the legacy pair
    // ========================================

    private ProfitAndLossEntity sellAndCapture(ProfitLossContext context, String financialYear) {
        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq(financialYear)))
                .thenReturn(Optional.empty());
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context);

        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ProfitLossContext sell(AssetType assetType, TradeSegment segment,
                                          LocalDate buyDate, LocalDate sellDate) {
        return new ProfitLossContext(
                "txn-d8", 10.0, sellDate, 150.0, STOCK_CODE, BROKER, EXCHANGE, assetType,
                TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of(new BuyContext(10.0, buyDate, 100.0)), segment);
    }

    @Test
    void updateProfitAndLoss_sellShortTerm_recordsTheClassificationInTheMap() {
        // Given
        ProfitLossContext context = sell(AssetType.EQUITY, TradeSegment.DELIVERY,
                LocalDate.of(2023, 1, 15), LocalDate.of(2023, 12, 15));

        // When
        ProfitAndLossEntity saved = sellAndCapture(context, "2023-2024");

        // Then
        Map<CapitalGainsType, FinancialReport> byType = saved.getRealisedProfits().getGainsByClassification();
        assertEquals(Set.of(CapitalGainsType.SHORT_TERM), byType.keySet());
        MoneyAssert.assertMoney(1500.0, byType.get(CapitalGainsType.SHORT_TERM).getSellAmount());
    }

    @Test
    void updateProfitAndLoss_sellIntradayEquity_recordsSpeculativeRatherThanACapitalGain() {
        // Given -- bought and sold the same day, intraday
        LocalDate sameDay = LocalDate.of(2023, 12, 15);
        ProfitLossContext context = sell(AssetType.EQUITY, TradeSegment.INTRADAY, sameDay, sameDay);

        // When
        ProfitAndLossEntity saved = sellAndCapture(context, "2023-2024");

        // Then
        Map<CapitalGainsType, FinancialReport> byType = saved.getRealisedProfits().getGainsByClassification();
        assertEquals(Set.of(CapitalGainsType.SPECULATIVE), byType.keySet());
        assertFalse(CapitalGainsType.SPECULATIVE.countsAsCapitalGains());
    }

    @Test
    void updateProfitAndLoss_sellFixedDeposit_recordsADifferentHeadOfIncome() {
        // Given
        ProfitLossContext context = sell(AssetType.FD, TradeSegment.DELIVERY,
                LocalDate.of(2023, 1, 15), LocalDate.of(2023, 12, 15));

        // When
        ProfitAndLossEntity saved = sellAndCapture(context, "2023-2024");

        // Then -- interest income, which does not belong under capital gains at all
        Map<CapitalGainsType, FinancialReport> byType = saved.getRealisedProfits().getGainsByClassification();
        assertEquals(Set.of(CapitalGainsType.NOT_CAPITAL_GAINS), byType.keySet());
    }

    @Test
    void updateProfitAndLoss_sellMutualFundWithNoSubClass_recordsTheUnknownRatherThanGuessing() {
        // Given -- D7: the sub-class is unavailable until the instrument registry ships
        ProfitLossContext context = sell(AssetType.MUTUAL_FUND, TradeSegment.DELIVERY,
                LocalDate.of(2023, 1, 15), LocalDate.of(2025, 12, 15));

        // When
        ProfitAndLossEntity saved = sellAndCapture(context, "2025-2026");

        // Then
        Map<CapitalGainsType, FinancialReport> byType = saved.getRealisedProfits().getGainsByClassification();
        assertEquals(Set.of(CapitalGainsType.UNCLASSIFIED), byType.keySet());
    }

    @Test
    void updateProfitAndLoss_whenTheClassificationIsNotACapitalGain_theLegacyPairIsUnchanged() {
        // Given -- the cutover contract: existing readers must see exactly what they saw before
        LocalDate sameDay = LocalDate.of(2023, 12, 15);
        ProfitLossContext context = sell(AssetType.EQUITY, TradeSegment.INTRADAY, sameDay, sameDay);

        // When
        ProfitAndLossEntity saved = sellAndCapture(context, "2023-2024");

        // Then -- still counted short-term in the pair, as it is today, and no long-term leakage
        assertNotNull(saved.getRealisedProfits().getShortTermCapitalGains());
        assertNull(saved.getRealisedProfits().getLongTermCapitalGains());
        MoneyAssert.assertMoney(1500.0,
                saved.getRealisedProfits().getShortTermCapitalGains().getSellAmount());
    }

    @Test
    void getProfitAndLoss_carriesTheClassificationMapThroughToTheResponse() {
        // Given -- a stored period holding a speculative bucket, which the legacy pair cannot express
        ProfitAndLossEntity entity = new ProfitAndLossEntity(TEST_EMAIL, "2023-2024");
        RealisedProfits realisedProfits = RealisedProfits.empty();
        FinancialReport speculative = FinancialReport.empty();
        speculative.setSellAmount(1500.0);
        realisedProfits.setGainsByClassification(
                new EnumMap<>(Map.of(CapitalGainsType.SPECULATIVE, speculative)));
        entity.setRealisedProfits(realisedProfits);
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.of(entity));

        // When
        ProfitAndLossResponse response = profitAndLossService.getProfitAndLoss(
                UserMail.from(TEST_EMAIL), "2023-2024");

        // Then
        FinancialReportResponse mapped =
                response.getRealisedProfits().getGainsByClassification().get(CapitalGainsType.SPECULATIVE);
        assertNotNull(mapped, "the classification map must survive the response copy");
        MoneyAssert.assertMoney(1500.0, mapped.getSellAmount());
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
        realisedProfits.setShortTermCapitalGains(yearly);
        ProfitAndLossEntity entity = new ProfitAndLossEntity(TEST_EMAIL, "2023-2024");
        entity.setRealisedProfits(realisedProfits);
        when(profitAndLossRepository.findByEmailAndFinancialYear(TEST_EMAIL, "2023-2024"))
                .thenReturn(Optional.of(entity));

        // When
        ProfitAndLossResponse response = profitAndLossService.getProfitAndLoss(
                UserMail.from(TEST_EMAIL), "2023-2024");

        // Then
        FinancialReportResponse year = response.getRealisedProfits().getShortTermCapitalGains();
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
                    List.of(new BuyContext(1.0, LocalDate.of(2023, 6, 15), 0.05)),
                    TradeSegment.DELIVERY);
            profitAndLossService.updateProfitAndLoss(UserMail.from(TEST_EMAIL), context);
        }

        // Then -- exact equality, deliberately
        FinancialReport yearly = accumulating.getRealisedProfits().getShortTermCapitalGains();
        assertEquals(1.00, yearly.getSellAmount());
        assertEquals(0.50, yearly.getPurchaseAmount());
    }
}
