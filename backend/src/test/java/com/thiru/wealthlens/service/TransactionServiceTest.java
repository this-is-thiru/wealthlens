package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.portfolio.service.MongoTemplateService;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MongoTemplateService mongoTemplateService;

    @InjectMocks
    private TransactionService transactionService;

    private UserMail userMail;
    private AssetRequest assetRequest;

    @BeforeEach
    void setUp() {
        userMail = UserMail.from("test@example.com");
        assetRequest = new AssetRequest();
        assetRequest.setStockCode("AAPL");
        assetRequest.setBrokerName(BrokerName.ZERODHA);
        assetRequest.setTransactionType(TransactionType.BUY);
        assetRequest.setPrice(150.0);
        assetRequest.setQuantity(10.0);
        assetRequest.setTransactionDate(LocalDate.now());
    }

    @Test
    void addTransaction_withNullTempTransactionId_savesNormally() {
        assetRequest.setTempTransactionId(null);
        TransactionEntity saved = new TransactionEntity();
        saved.setId("new-id");
        when(transactionRepository.save(any(TransactionEntity.class))).thenReturn(saved);

        String result = transactionService.addTransaction(userMail, assetRequest);

        assertEquals("new-id", result);
        verify(transactionRepository).save(any(TransactionEntity.class));
    }

    @Test
    void addTransaction_withNewTempTransactionId_savesNormally() {
        String tempId = "temp-123";
        assetRequest.setTempTransactionId(tempId);
        when(transactionRepository.findByEmailAndSourceTempTransactionId("test@example.com", tempId))
                .thenReturn(Optional.empty());
        TransactionEntity saved = new TransactionEntity();
        saved.setId("new-id");
        when(transactionRepository.save(any(TransactionEntity.class))).thenReturn(saved);

        String result = transactionService.addTransaction(userMail, assetRequest);

        assertEquals("new-id", result);
        verify(transactionRepository).findByEmailAndSourceTempTransactionId("test@example.com", tempId);
        verify(transactionRepository).save(any(TransactionEntity.class));
    }

    @Test
    void addTransaction_withDuplicateTempTransactionId_returnsExistingIdWithoutSaving() {
        String tempId = "temp-123";
        assetRequest.setTempTransactionId(tempId);
        TransactionEntity existing = new TransactionEntity();
        existing.setId("existing-id");
        when(transactionRepository.findByEmailAndSourceTempTransactionId("test@example.com", tempId))
                .thenReturn(Optional.of(existing));

        String result = transactionService.addTransaction(userMail, assetRequest);

        assertEquals("existing-id", result);
        verify(transactionRepository).findByEmailAndSourceTempTransactionId("test@example.com", tempId);
        verify(transactionRepository, never()).save(any(TransactionEntity.class));
    }

    @Test
    void addTransaction_whenNoDateIsGiven_recordsToday() {
        // Given -- a trade sent without a date (W-287)
        assetRequest.setTransactionDate(null);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate before = LocalDate.now();

        // When
        transactionService.addTransaction(userMail, assetRequest);

        // Then -- dated today; bounded on both sides so a run across midnight still holds
        LocalDate stored = storedTransaction().getTransactionDate();
        LocalDate after = LocalDate.now();
        assertFalse(stored.isBefore(before));
        assertFalse(stored.isAfter(after));
    }

    @Test
    void addTransaction_whenDated_keepsTheDateAndStoresTheCallersEmail() {
        // Given -- a trade dated 5 Jan 2026 (W-287)
        assetRequest.setTransactionDate(LocalDate.of(2026, 1, 5));
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        transactionService.addTransaction(userMail, assetRequest);

        // Then
        TransactionEntity stored = storedTransaction();
        assertEquals(LocalDate.of(2026, 1, 5), stored.getTransactionDate());
        assertEquals("test@example.com", stored.getEmail());
    }

    @Test
    void getUserTransactions_whenFiltered_returnsTheMatchingDocuments() {
        // Given (W-287)
        List<QueryFilter> filters = List.of();
        List<TransactionEntity> found = List.of(transaction("t-1"));
        when(mongoTemplateService.getDocuments(userMail, filters, TransactionEntity.class)).thenReturn(found);

        // When / Then
        assertEquals(found, transactionService.getUserTransactions(userMail, filters));
    }

    @Test
    void getUserTransactions_whenUnfiltered_returnsEveryTradeOfTheUser() {
        // Given (W-287)
        List<TransactionEntity> found = List.of(transaction("t-1"));
        when(transactionRepository.findByEmail("test@example.com")).thenReturn(found);

        // When / Then
        assertEquals(found, transactionService.getUserTransactions(userMail));
    }

    @Test
    void transactionsForCorporateActions_whenAsked_returnsTheTradesBeforeTheRecordDate() {
        // Given (W-287)
        LocalDate recordDate = LocalDate.of(2026, 1, 5);
        List<TransactionEntity> found = List.of(transaction("t-1"));
        when(transactionRepository.findByStockCodeAndTransactionDateBeforeOrderByTransactionDateDesc("AAPL", recordDate))
                .thenReturn(found);

        // When / Then
        assertEquals(found, transactionService.transactionsForCorporateActions("AAPL", recordDate));
    }

    @Test
    void testTransactionsForCorporateActions_whenAsked_returnsTheUsersTradesAtTheBroker() {
        // Given (W-287)
        LocalDate recordDate = LocalDate.of(2026, 1, 5);
        List<TransactionEntity> found = List.of(transaction("t-1"));
        when(transactionRepository.findByEmailAndStockCodeAndBrokerNameAndTransactionDateBeforeOrderByTransactionDateDesc(
                "test@example.com", "AAPL", BrokerName.ZERODHA, recordDate)).thenReturn(found);

        // When / Then
        assertEquals(found, transactionService.testTransactionsForCorporateActions(
                "test@example.com", "AAPL", BrokerName.ZERODHA, recordDate));
    }

    @Test
    void saveCorporateActionProcessedTransactions_whenSaved_returnsTheirIds() {
        // Given (W-287)
        List<TransactionEntity> trades = List.of(transaction("t-1"), transaction("t-2"));
        when(transactionRepository.saveAll(trades)).thenReturn(trades);

        // When / Then
        assertEquals(List.of("t-1", "t-2"), transactionService.saveCorporateActionProcessedTransactions(trades));
    }

    @Test
    void deleteTransactions_whenAsked_deletesTheUsersTrades() {
        // When (W-287)
        transactionService.deleteTransactions(userMail);

        // Then
        verify(transactionRepository).deleteByEmail("test@example.com");
    }

    private TransactionEntity storedTransaction() {
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static TransactionEntity transaction(String id) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(id);
        return entity;
    }
}
