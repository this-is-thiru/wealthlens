package com.thiru.wealthlens.portfolio.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.config.IdempotencyProperties;
import com.thiru.wealthlens.portfolio.dto.TransactionResponse;
import com.thiru.wealthlens.portfolio.dto.charges.ChargeNote;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** That a transaction response carries its own id, and the charges recorded against it. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceChargesTest {

    private static final String EMAIL = "test@example.com";
    private static final UserMail USER = UserMail.from(EMAIL);

    @Mock private TransactionRepository transactionRepository;
    @Mock private MongoTemplateService mongoTemplateService;
    @Mock private ChargeViewAssembler chargeViewAssembler;

    private TransactionService service() {
        return new TransactionService(transactionRepository, new IdempotencyProperties(60),
                mongoTemplateService, chargeViewAssembler);
    }

    /**
     * {@code TransactionEntity.id} is {@code @JsonIgnore} and the mapping goes through JSON, so the
     * id has to be carried across by hand. Without it a caller cannot line a trade up against
     * anything, charges included.
     */
    @Test
    @DisplayName("getAllUserTransactions: carries the transaction id across the JSON copy")
    void getAllUserTransactions_whenMapped_carriesTheTransactionId() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(transaction("txn-1")));
        when(chargeViewAssembler.forTransactions(anyString(), any()))
                .thenReturn(ChargeViewAssembler.ChargeLookup.empty());

        // When
        List<TransactionResponse> responses = service().getAllUserTransactions(USER);

        // Then
        assertEquals("txn-1", responses.getFirst().getTransactionId());
    }

    @Test
    @DisplayName("getAllUserTransactions: hangs the recorded charges off the trade")
    void getAllUserTransactions_whenChargesWereRecorded_attachesThem() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(transaction("txn-1")));
        when(chargeViewAssembler.forTransactions(anyString(), any()))
                .thenReturn(ChargeViewAssembler.ChargeLookup.empty());
        when(chargeViewAssembler.transactionNote(any(), eq("txn-1"))).thenReturn(note(23.60));

        // When
        List<TransactionResponse> responses = service().getAllUserTransactions(USER);

        // Then
        assertMoney(23.60, responses.getFirst().getCharges().getTotal());
    }

    /** A V1 trade, or one made before the engine was switched on, was never priced and never will be. */
    @Test
    @DisplayName("getAllUserTransactions: an unpriced trade reports null rather than zero")
    void getAllUserTransactions_whenNeverPriced_leavesChargesNull() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(transaction("txn-1")));
        when(chargeViewAssembler.forTransactions(anyString(), any()))
                .thenReturn(ChargeViewAssembler.ChargeLookup.empty());
        when(chargeViewAssembler.transactionNote(any(), anyString())).thenReturn(null);

        // When
        List<TransactionResponse> responses = service().getAllUserTransactions(USER);

        // Then
        assertNull(responses.getFirst().getCharges());
    }

    /** The N+1 this was written to avoid: one lookup for the page, not one per trade. */
    @Test
    @DisplayName("getAllUserTransactions: looks the charges up once for the whole page")
    void getAllUserTransactions_whenManyTrades_looksChargesUpOnce() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(
                List.of(transaction("txn-1"), transaction("txn-2"), transaction("txn-3")));
        when(chargeViewAssembler.forTransactions(anyString(), any()))
                .thenReturn(ChargeViewAssembler.ChargeLookup.empty());

        // When
        service().getAllUserTransactions(USER);

        // Then
        verify(chargeViewAssembler, times(1)).forTransactions(eq(EMAIL), any());
    }

    private static TransactionEntity transaction(String id) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail(EMAIL);
        transaction.setStockCode("INFY");
        transaction.setStockName("Infosys");
        transaction.setExchangeName("NSE");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setTransactionDate(LocalDate.of(2025, 4, 1));
        transaction.setQuantity(10D);
        transaction.setPrice(1500D);
        return transaction;
    }

    private static ChargeNote note(double total) {
        ChargeNote note = new ChargeNote();
        note.setVerbatim(true);
        note.setByCode(Map.of("BROKERAGE", total));
        note.setTotal(total);
        note.setSourceCount(1);
        return note;
    }
}
