package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.portfolio.service.MongoTemplateService;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @BeforeEach
    void setUp() {
        userMail = UserMail.from("test@example.com");
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

    private static TransactionEntity transaction(String id) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(id);
        return entity;
    }
}
