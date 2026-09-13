package com.thiru.wealthlens.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.thiru.wealthlens.brokercharges.service.ChargeAccountService;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.ErasureReport;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Erasing everything one user owns.
 *
 * <p>The behaviour that matters is not that each delete happens — it is that one failing delete
 * neither stops the others nor is reported as success.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UserRecordsErasureServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final UserMail USER = UserMail.from(EMAIL);

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private TradeOutcomeService tradeOutcomeService;
    @Mock private TransactionService transactionService;
    @Mock private ProfitAndLossService profitAndLossService;
    @Mock private TemporaryTransactionService temporaryTransactionService;
    @Mock private UserChargeService userChargeService;
    @Mock private ChargeAccountService chargeAccountService;

    @InjectMocks private UserRecordsErasureService service;

    /** Every collection this service is responsible for, and nothing left behind. */
    @Test
    @DisplayName("erase: clears every collection the user owns")
    void erase_whenCalled_clearsEveryCollection() {
        // When
        ErasureReport report = service.erase(USER);

        // Then
        verify(portfolioRepository).deleteByEmail(EMAIL);
        verify(tradeOutcomeService).deleteByEmail(USER);
        verify(transactionService).deleteTransactions(USER);
        verify(profitAndLossService).deleteProfitAndLoss(USER);
        verify(temporaryTransactionService).deleteTemporaryTransaction(USER);
        verify(userChargeService).deleteByEmail(EMAIL);
        verify(chargeAccountService).deleteByEmail(EMAIL);
        assertTrue(report.isComplete());
        assertEquals(List.of(), report.getFailed());
    }

    /**
     * Without a working transaction manager the deletes are independent, so stopping at the first
     * failure would leave more behind than carrying on does.
     */
    @Test
    @DisplayName("erase: one failing delete does not stop the others")
    void erase_whenOneDeleteFails_stillClearsTheRest() {
        // Given
        doThrow(new RuntimeException("mongo down")).when(transactionService).deleteTransactions(USER);

        // When
        service.erase(USER);

        // Then
        verify(portfolioRepository).deleteByEmail(EMAIL);
        verify(userChargeService).deleteByEmail(EMAIL);
        verify(chargeAccountService).deleteByEmail(EMAIL);
    }

    /**
     * The defect this replaces. Failures were caught, logged and then discarded, so the caller was
     * told "deleted successfully" over data that was still there.
     */
    @Test
    @DisplayName("erase: a failed delete is reported, not swallowed into a success")
    void erase_whenOneDeleteFails_saysWhichOne() {
        // Given
        doThrow(new RuntimeException("mongo down")).when(userChargeService).deleteByEmail(EMAIL);

        // When
        ErasureReport report = service.erase(USER);

        // Then
        assertFalse(report.isComplete());
        assertEquals(List.of("user_charges"), report.getFailed());
    }

    @Test
    @DisplayName("erase: every failure is named, not only the first")
    void erase_whenSeveralDeletesFail_namesThemAll() {
        // Given
        doThrow(new RuntimeException("mongo down")).when(portfolioRepository).deleteByEmail(EMAIL);
        doThrow(new RuntimeException("mongo down")).when(chargeAccountService).deleteByEmail(EMAIL);

        // When
        ErasureReport report = service.erase(USER);

        // Then
        assertEquals(List.of("assets", "charge_accounts"), report.getFailed());
    }
}
