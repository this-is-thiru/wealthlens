package com.thiru.wealthlens.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.config.IdempotencyProperties;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.context.TransactionRecord;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceIdempotencyTest {

    private static final String EMAIL = "test@example.com";
    private static final UserMail USER = UserMail.from(EMAIL);

    /** Midnight of 1 February 2026 in Asia/Kolkata: 18:30 on 31 January in UTC, the time a submission is stored at (W-402). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-31T18:30:00Z"), ZoneId.of("Asia/Kolkata"));

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ChargeViewAssembler chargeViewAssembler;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, new IdempotencyProperties(60),
                chargeViewAssembler, CLOCK);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity saved = invocation.getArgument(0);
            saved.setId("txn-new");
            return saved;
        });
    }

    private static AssetRequest trade() {
        AssetRequest request = new AssetRequest();
        request.setStockCode("INFY");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAccountHolder("main");
        request.setAssetType(AssetType.EQUITY);
        request.setTransactionType(TransactionType.BUY);
        request.setQuantity(10.0);
        request.setPrice(1500.0);
        request.setTransactionDate(LocalDate.of(2025, 6, 10));
        return request;
    }

    private static TransactionEntity existing(String id) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(id);
        return entity;
    }

    @Test
    @DisplayName("a first submission is recorded and is not a replay")
    void recordTransaction_whenNew_writesIt() {
        // Given / When
        TransactionRecord record = transactionService.recordTransaction(USER, trade(), TradeFlow.V2);

        // Then
        assertEquals("txn-new", record.transactionId());
        assertFalse(record.replay());
        verify(transactionRepository).save(any(TransactionEntity.class));
    }

    @Test
    @DisplayName("a client key that has been seen returns the original and writes nothing")
    void recordTransaction_whenTheClientKeyIsKnown_isAReplay() {
        // Given
        AssetRequest request = trade();
        request.setIdempotencyKey("client-key-1");
        when(transactionRepository.findByEmailAndIdempotencyKey(EMAIL, "client-key-1"))
                .thenReturn(Optional.of(existing("txn-original")));

        // When
        TransactionRecord record = transactionService.recordTransaction(USER, request, TradeFlow.V2);

        // Then
        assertEquals("txn-original", record.transactionId());
        assertTrue(record.replay());
        verify(transactionRepository, never()).save(any(TransactionEntity.class));
    }

    @Test
    @DisplayName("an identical trade inside the window is a retry, and nothing is written")
    void recordTransaction_whenAnIdenticalTradeIsRecent_isAReplay() {
        // Given
        when(transactionRepository.findFirstByEmailAndTradeFingerprintAndSubmittedAtAfterOrderBySubmittedAtDesc(
                eq(EMAIL), any(), any())).thenReturn(Optional.of(existing("txn-seconds-ago")));

        // When
        TransactionRecord record = transactionService.recordTransaction(USER, trade(), TradeFlow.V2);

        // Then -- the window is 60 s back from now in UTC, the zone submitted_at is stored in (W-402)
        assertEquals("txn-seconds-ago", record.transactionId());
        assertTrue(record.replay());
        verify(transactionRepository, never()).save(any(TransactionEntity.class));
        verify(transactionRepository).findFirstByEmailAndTradeFingerprintAndSubmittedAtAfterOrderBySubmittedAtDesc(eq(EMAIL), any(),
                eq(LocalDateTime.of(2026, 1, 31, 18, 29)));
    }

    @Test
    @DisplayName("two genuinely identical trades outside the window are both accepted -- D4 requires this")
    void recordTransaction_whenTheIdenticalTradeIsOld_isNotAReplay() {
        // Given -- the query finds nothing because the earlier one falls outside the window
        when(transactionRepository.findFirstByEmailAndTradeFingerprintAndSubmittedAtAfterOrderBySubmittedAtDesc(
                eq(EMAIL), any(), any())).thenReturn(Optional.empty());

        // When
        TransactionRecord record = transactionService.recordTransaction(USER, trade(), TradeFlow.V2);

        // Then
        assertFalse(record.replay());
        verify(transactionRepository).save(any(TransactionEntity.class));
    }

    @Test
    @DisplayName("a client key beats the fingerprint, so two identical trades seconds apart are both accepted")
    void recordTransaction_whenAKeyIsSupplied_theFingerprintWindowIsNotConsulted() {
        // Given -- the escape hatch for a bulk upload with genuinely duplicate rows
        AssetRequest request = trade();
        request.setIdempotencyKey("row-2");
        when(transactionRepository.findByEmailAndIdempotencyKey(EMAIL, "row-2")).thenReturn(Optional.empty());

        // When
        TransactionRecord record = transactionService.recordTransaction(USER, request, TradeFlow.V2);

        // Then
        assertFalse(record.replay());
        verify(transactionRepository, never())
                .findFirstByEmailAndTradeFingerprintAndSubmittedAtAfterOrderBySubmittedAtDesc(any(), any(), any());
    }

    @Test
    @DisplayName("a redriven temporary transaction is recognised, so the trade is not applied twice")
    void recordTransaction_whenTheTemporaryTransactionWasAlreadyRedriven_isAReplay() {
        // Given
        AssetRequest request = trade();
        request.setTempTransactionId("temp-1");
        when(transactionRepository.findByEmailAndSourceTempTransactionId(EMAIL, "temp-1"))
                .thenReturn(Optional.of(existing("txn-first-redrive")));

        // When
        TransactionRecord record = transactionService.recordTransaction(USER, request, TradeFlow.V2);

        // Then
        assertEquals("txn-first-redrive", record.transactionId());
        assertTrue(record.replay());
    }

    @Test
    @DisplayName("what gets written carries the fingerprint and the submission time")
    void recordTransaction_whenNew_stampsTheFingerprintAndSubmissionTime() {
        // When
        transactionService.recordTransaction(USER, trade(), TradeFlow.V2);

        // Then
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        TransactionEntity saved = captor.getValue();
        assertEquals(TradeFingerprint.of(EMAIL, trade()), saved.getTradeFingerprint());
        assertEquals(LocalDateTime.of(2026, 1, 31, 18, 30), saved.getSubmittedAt());
    }

    @Test
    @DisplayName("a zero window disables the fingerprint fallback but leaves client keys working")
    void recordTransaction_whenTheWindowIsZero_skipsTheFingerprintLookup() {
        // Given
        transactionService = new TransactionService(transactionRepository, new IdempotencyProperties(0),
                chargeViewAssembler, CLOCK);

        // When
        TransactionRecord record = transactionService.recordTransaction(USER, trade(), TradeFlow.V2);

        // Then
        assertFalse(record.replay());
        verify(transactionRepository, never())
                .findFirstByEmailAndTradeFingerprintAndSubmittedAtAfterOrderBySubmittedAtDesc(any(), any(), any());
    }

    @Test
    @DisplayName("a V2 trade sent without a date is recorded as today's")
    void recordTransaction_whenNoDateIsGiven_recordsToday() {
        // Given (W-287)
        AssetRequest request = trade();
        request.setTransactionDate(null);

        // When
        transactionService.recordTransaction(USER, request, TradeFlow.V2);

        // Then -- the business zone's date: midnight of 1 February in Kolkata, while UTC is still on 31 January (D-958)
        assertEquals(LocalDate.of(2026, 2, 1), storedTransaction().getTransactionDate());
    }

    @Test
    @DisplayName("a dated trade keeps its date and is stored under the caller's email")
    void recordTransaction_whenDated_keepsTheDateAndStoresTheCallersEmail() {
        // Given -- a trade dated 5 Jan 2026; moved here from the removed addTransaction's test (W-290)
        AssetRequest request = trade();
        request.setTransactionDate(LocalDate.of(2026, 1, 5));

        // When
        transactionService.recordTransaction(USER, request, TradeFlow.V1);

        // Then
        TransactionEntity stored = storedTransaction();
        assertEquals(LocalDate.of(2026, 1, 5), stored.getTransactionDate());
        assertEquals(EMAIL, stored.getEmail());
    }

    @Test
    @DisplayName("a V2 trade is stored under the caller's email with the client's key")
    void recordTransaction_whenNew_storesTheCallersEmailAndKey() {
        // Given (W-287)
        AssetRequest request = trade();
        request.setIdempotencyKey("key-1");

        // When
        transactionService.recordTransaction(USER, request, TradeFlow.V2);

        // Then
        TransactionEntity stored = storedTransaction();
        assertEquals(EMAIL, stored.getEmail());
        assertEquals("key-1", stored.getIdempotencyKey());
    }

    @Test
    @DisplayName("a trade is stored with the flow that received it")
    void recordTransaction_whenNew_storesTheFlowThatReceivedIt() {
        // When (W-283a1, D-580)
        transactionService.recordTransaction(USER, trade(), TradeFlow.V1);

        // Then
        assertEquals(TradeFlow.V1, storedTransaction().getTradeFlow());
    }

    private TransactionEntity storedTransaction() {
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
