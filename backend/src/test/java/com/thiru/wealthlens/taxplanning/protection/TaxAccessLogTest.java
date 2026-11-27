package com.thiru.wealthlens.taxplanning.protection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class TaxAccessLogTest {

    @Mock
    private AccessRecordRepository repository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearTransactionFlags() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void record_whenTheTransactionIsReadOnly_refusesAndSavesNothing() {
        // Given
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        TaxAccessLog log = new TaxAccessLog(repository, clock);

        // When / Then
        assertThatThrownBy(() -> log.record("owner@example.com", ReadSubject.COMPUTATION, "receipt-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPUTATION");
        verify(repository, never()).save(any());
    }

    @Test
    void record_whenTheTransactionWrites_savesTheRead() {
        // Given
        TaxAccessLog log = new TaxAccessLog(repository, clock);

        // When
        log.record("owner@example.com", ReadSubject.COMPUTATION, "receipt-1");

        // Then
        verify(repository).save(any(AccessRecord.class));
    }
}
