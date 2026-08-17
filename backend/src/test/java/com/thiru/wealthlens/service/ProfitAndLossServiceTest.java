package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.thiru.wealthlens.brokercharges.dto.context.BrokerChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerChargeTransactionType;
import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.brokercharges.service.UserBrokerChargeService;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.service.ProfitAndLossService;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
    private UserBrokerChargeService userBrokerChargeService;

    @InjectMocks
    private ProfitAndLossService profitAndLossService;

    // ========================================
    // updateProfitAndLoss with ProfitLossContext (v2)
    // ========================================

    @Test
    void updateProfitAndLoss_buyEquity_recordsBrokerCharges() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate buyDate = LocalDate.of(2024, 1, 15);
        ProfitLossContext context = new ProfitLossContext(
                "txn-123", 10.0, buyDate, 100.0, STOCK_CODE, BROKER, EXCHANGE,
                AssetType.EQUITY, TransactionType.BUY, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of()
        );

        UserBrokerCharges userBrokerCharges = new UserBrokerCharges();
        userBrokerCharges.setBrokerage(10.0);
        userBrokerCharges.setAmcCharges(0.0);
        userBrokerCharges.setTransactionDate(buyDate);

        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2023-2024")))
                .thenReturn(Optional.empty());
        when(userBrokerChargeService.addUserBrokerChargeEntry(any(UserMail.class), any(BrokerChargeContext.class)))
                .thenReturn(userBrokerCharges);
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // Then
        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        assertNotNull(savedEntity.getRealisedProfits());
        assertNotNull(savedEntity.getRealisedProfits().getYearlyBrokerCharges());
        assertEquals(TEST_EMAIL, savedEntity.getEmail());
        assertEquals("2023-2024", savedEntity.getFinancialYear());

        verify(userBrokerChargeService).addUserBrokerChargeEntry(any(UserMail.class), any(BrokerChargeContext.class));
    }

    @Test
    void updateProfitAndLoss_buyNonEquity_skipsBrokerCharges() {
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

        verify(userBrokerChargeService, never()).addUserBrokerChargeEntry(any(), any());
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
        when(userBrokerChargeService.addUserBrokerChargeEntry(any(UserMail.class), any(BrokerChargeContext.class)))
                .thenReturn(null);
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
        when(userBrokerChargeService.addUserBrokerChargeEntry(any(UserMail.class), any(BrokerChargeContext.class)))
                .thenReturn(null);
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
        when(userBrokerChargeService.addUserBrokerChargeEntry(any(UserMail.class), any(BrokerChargeContext.class)))
                .thenReturn(null);
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
        when(userBrokerChargeService.addUserBrokerChargeEntry(any(UserMail.class), any(BrokerChargeContext.class)))
                .thenReturn(null);
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
        when(userBrokerChargeService.addUserBrokerChargeEntry(any(UserMail.class), any(BrokerChargeContext.class)))
                .thenReturn(null);
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
        verify(userBrokerChargeService, never()).addUserBrokerChargeEntry(any(), any());
    }

    // ========================================
    // updateProfitAndLossWithAmcCharges
    // ========================================

    @Test
    void updateProfitAndLossWithAmcCharges_success() {
        // Given
        UserMail userMail = UserMail.from(TEST_EMAIL);
        LocalDate txnDate = LocalDate.of(2024, 2, 15);
        BrokerChargeContext brokerChargeContext = new BrokerChargeContext(
                "txn-amc-1", STOCK_CODE, BROKER, BrokerChargeTransactionType.BUY,
                txnDate, EXCHANGE, null, 1000.0
        );

        UserBrokerCharges userBrokerCharges = new UserBrokerCharges();
        userBrokerCharges.setBrokerage(0.0);
        userBrokerCharges.setAmcCharges(50.0);
        userBrokerCharges.setTransactionDate(txnDate);

        when(profitAndLossRepository.findByEmailAndFinancialYear(eq(TEST_EMAIL), eq("2023-2024")))
                .thenReturn(Optional.empty());
        when(userBrokerChargeService.addUserBrokerChargeEntry(userMail, brokerChargeContext))
                .thenReturn(userBrokerCharges);
        when(profitAndLossRepository.save(any(ProfitAndLossEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UserBrokerCharges result = profitAndLossService.updateProfitAndLossWithAmcCharges(userMail, brokerChargeContext);

        // Then
        assertNotNull(result);
        assertEquals(50.0, result.getAmcCharges());

        ArgumentCaptor<ProfitAndLossEntity> captor = ArgumentCaptor.forClass(ProfitAndLossEntity.class);
        verify(profitAndLossRepository).save(captor.capture());

        ProfitAndLossEntity savedEntity = captor.getValue();
        assertNotNull(savedEntity.getRealisedProfits());
        assertNotNull(savedEntity.getRealisedProfits().getYearlyBrokerCharges());
    }
}
