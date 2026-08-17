package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.repository.CorporateActionRepository;
import com.thiru.wealthlens.corporate.repository.LastlyPerformedCorporateActionRepo;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemporaryTransactionServiceTest {

    @Mock
    private CorporateActionRepository corporateActionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LastlyPerformedCorporateActionRepo lastlyPerformedCorporateActionRepo;

    private TemporaryTransactionService service;

    private UserMail userMail;
    private AssetRequest assetRequest;

    @BeforeEach
    void setUp() {
        service = new TemporaryTransactionService(
                corporateActionRepository,
                transactionRepository,
                lastlyPerformedCorporateActionRepo
        );
        userMail = UserMail.from("test@example.com");
        assetRequest = new AssetRequest();
        assetRequest.setStockCode("RELIANCE");
        assetRequest.setBrokerName(BrokerName.ZERODHA);
        assetRequest.setTransactionDate(LocalDate.of(2025, 1, 15));
        assetRequest.setTransactionType(TransactionType.BUY);
        assetRequest.setAssetType(AssetType.EQUITY);
        assetRequest.setPrice(100.0);
        assetRequest.setQuantity(10.0);
    }

    @Test
    void filterOutTransaction_savesTransactionEntityWithStatusTemporary() {
        // Given: a corporate action blocks the transaction
        when(corporateActionRepository.findByStockCodeAndTypeInAndRecordDateBetween(
                any(), any(), any(), any()
        )).thenReturn(List.of());

        // When
        String tempId = service.filterOutTransaction(userMail, assetRequest);

        // Then: no block found, so null is returned
        assertNull(tempId);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void filterOutTransaction_whenCorporateActionBlocks_savesEntityWithStatusTemporary() {
        // Given: a corporate action blocks the transaction
        CorporateActionEntity corporateAction = new CorporateActionEntity();
        corporateAction.setStockCode("RELIANCE");
        corporateAction.setRecordDate(LocalDate.of(2025, 1, 10));
        corporateAction.setExDate(LocalDate.of(2025, 1, 12));
        corporateAction.setType(CorporateActionType.BONUS);
        corporateAction.setAssetType(AssetType.EQUITY);

        when(corporateActionRepository.findByStockCodeAndTypeInAndRecordDateBetween(
                eq("RELIANCE"),
                any(),
                any(),
                eq(LocalDate.of(2025, 1, 15))
        )).thenReturn(List.of(corporateAction));

        when(lastlyPerformedCorporateActionRepo.findLastPerformedCA(
                any(), any(), any(), any(), any()
        )).thenReturn(Optional.empty());

        // Setup the entity that will be returned when save is called
        TransactionEntity savedEntity = new TransactionEntity();
        savedEntity.setId("temp-123");
        savedEntity.setStatus(TransactionStatus.TEMPORARY);
        when(transactionRepository.save(any(TransactionEntity.class))).thenReturn(savedEntity);

        // When
        String tempId = service.filterOutTransaction(userMail, assetRequest);

        // Then
        assertEquals("temp-123", tempId);
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        TransactionEntity saved = captor.getValue();
        assertEquals(TransactionStatus.TEMPORARY, saved.getStatus());
        assertEquals(assetRequest, saved.getAssetRequest());
        assertEquals("test@example.com", saved.getEmail());
    }

    @Test
    void getAllTemporaryTransactions_callsFindByEmailAndStatusWithTemporary() {
        // Given
        List<TransactionEntity> expected = List.of(new TransactionEntity());
        when(transactionRepository.findByEmailAndStatus("test@example.com", TransactionStatus.TEMPORARY))
                .thenReturn(expected);

        // When
        List<TransactionEntity> result = service.getAllTemporaryTransactions(userMail);

        // Then
        assertEquals(expected, result);
        verify(transactionRepository).findByEmailAndStatus("test@example.com", TransactionStatus.TEMPORARY);
    }

    @Test
    void getAllTemporaryTransactions_returnsEmptyListWhenNoTempTransactions() {
        // Given
        when(transactionRepository.findByEmailAndStatus("test@example.com", TransactionStatus.TEMPORARY))
                .thenReturn(Collections.emptyList());

        // When
        List<TransactionEntity> result = service.getAllTemporaryTransactions(userMail);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteTemporaryTransaction_deletesByEmailAndStatusTemporary() {
        // When
        service.deleteTemporaryTransaction(userMail);

        // Then
        verify(lastlyPerformedCorporateActionRepo).deleteByEmail("test@example.com");
        verify(transactionRepository).deleteByEmailAndStatus("test@example.com", TransactionStatus.TEMPORARY);
    }

    @Test
    void filterOutTransaction_withCheckFalse_returnsFalse() {
        // When
        boolean result = service.filterOutTransaction(userMail, assetRequest, false);

        // Then
        assertFalse(result);
        verifyNoInteractions(corporateActionRepository);
    }

    @Test
    void findTempTransactionsBefore_delegatesToRepository() {
        // Given
        LocalDate recordDate = LocalDate.of(2025, 1, 20);
        List<TransactionEntity> expected = List.of(new TransactionEntity());
        when(transactionRepository.findByEmailAndStatusAndStockCodeAndAssetTypeAndTransactionDateBefore(
                "test@example.com", TransactionStatus.TEMPORARY, "RELIANCE", AssetType.EQUITY, recordDate
        )).thenReturn(expected);

        // When
        List<TransactionEntity> result = service.findTempTransactionsBefore(
                "test@example.com", "RELIANCE", AssetType.EQUITY, recordDate);

        // Then
        assertEquals(expected, result);
    }

    @Test
    void findTempTransactionsAfter_delegatesToRepository() {
        // Given
        LocalDate recordDate = LocalDate.of(2025, 1, 10);
        List<TransactionEntity> expected = List.of(new TransactionEntity());
        when(transactionRepository.findByEmailAndStatusAndStockCodeAndAssetTypeAndTransactionDateAfterOrderByTransactionDateAsc(
                "test@example.com", TransactionStatus.TEMPORARY, "RELIANCE", AssetType.EQUITY, recordDate
        )).thenReturn(expected);

        // When
        List<TransactionEntity> result = service.findTempTransactionsAfter(
                "test@example.com", "RELIANCE", AssetType.EQUITY, recordDate);

        // Then
        assertEquals(expected, result);
    }
}
