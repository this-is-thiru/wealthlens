package com.thiru.wealthlens.brokercharges.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeReconciliationResponse;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The report Phase B exists to produce: what the engine computed against what the user typed.
 *
 * <p>Its whole value is that a reader can act on a delta, so the cases that matter most are the
 * ones where a naive implementation would report a delta that is not real — a trade whose entered
 * figure is unknown, and a computation that resolved nothing. Both are called out rather than
 * subtracted, because a shadow row reading "computed 0, entered 120, delta -120" is
 * indistinguishable from a genuine overcharge unless the report says the card was missing.
 */
@ExtendWith(MockitoExtension.class)
class ChargeReconciliationServiceTest {

    private static final String EMAIL = "recon@wealthlens.test";

    @Mock
    private UserChargeRepository userChargeRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ChargeReconciliationService chargeReconciliationService;

    private static UserChargeEntity computed(String transactionId, double total, ChargeResolution resolution) {
        UserChargeEntity charge = new UserChargeEntity();
        charge.setEmail(EMAIL);
        charge.setTransactionId(transactionId);
        charge.setStockCode("RELIANCE");
        charge.setBrokerName(BrokerName.ZERODHA);
        charge.setAssetType(AssetType.EQUITY);
        charge.setEvent(ChargeEvent.BUY);
        charge.setTransactionDate(LocalDate.of(2025, 6, 10));
        charge.setTotalCharges(total);
        charge.setResolution(resolution);
        charge.setScheduleCode("ZERODHA_EQ_DELIVERY_2025_04");
        return charge;
    }

    private static TransactionEntity entered(String transactionId, double brokerCharges) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(transactionId);
        transaction.setEmail(EMAIL);
        transaction.setBrokerCharges(brokerCharges);
        return transaction;
    }

    @Test
    void reconcile_whenBothFiguresArePresent_reportsTheDelta() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL))
                .thenReturn(List.of(computed("txn-1", 23.45, ChargeResolution.RESOLVED)));
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(entered("txn-1", 20.00)));

        // When
        ChargeReconciliationResponse response = chargeReconciliationService.reconcile(EMAIL);

        // Then
        assertThat(response.rows()).hasSize(1);
        ChargeReconciliationResponse.Row row = response.rows().getFirst();
        assertThat(row.transactionId()).isEqualTo("txn-1");
        assertThat(row.computed()).isEqualTo(23.45);
        assertThat(row.entered()).isEqualTo(20.00);
        assertThat(row.delta()).isEqualTo(3.45);
        assertThat(row.comparable()).isTrue();
    }

    /**
     * Floating-point subtraction of two two-decimal figures is not itself two decimals. A report
     * whose deltas read 3.4499999999999993 invites the reader to distrust the whole column.
     */
    @Test
    void reconcile_roundsTheDeltaToPaise() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL))
                .thenReturn(List.of(computed("txn-1", 119.67, ChargeResolution.RESOLVED)));
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(entered("txn-1", 119.20)));

        // When
        ChargeReconciliationResponse response = chargeReconciliationService.reconcile(EMAIL);

        // Then
        assertThat(response.rows().getFirst().delta()).isEqualTo(0.47);
        assertThat(response.totalDelta()).isEqualTo(0.47);
    }

    @Test
    void reconcile_sumsEachColumnAcrossRows() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL))
                .thenReturn(List.of(
                        computed("txn-1", 23.45, ChargeResolution.RESOLVED),
                        computed("txn-2", 10.10, ChargeResolution.RESOLVED)));
        when(transactionRepository.findByEmail(EMAIL))
                .thenReturn(List.of(entered("txn-1", 20.00), entered("txn-2", 12.00)));

        // When
        ChargeReconciliationResponse response = chargeReconciliationService.reconcile(EMAIL);

        // Then
        assertThat(response.totalComputed()).isEqualTo(33.55);
        assertThat(response.totalEntered()).isEqualTo(32.00);
        assertThat(response.totalDelta()).isEqualTo(1.55);
        assertThat(response.comparableCount()).isEqualTo(2);
    }

    /**
     * A shadow row whose transaction is gone has no entered figure. Reporting it as zero would show
     * the engine's whole total as an overcharge.
     */
    @Test
    void reconcile_whenTheTransactionIsMissing_marksTheRowNotComparable() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL))
                .thenReturn(List.of(computed("txn-gone", 23.45, ChargeResolution.RESOLVED)));
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of());

        // When
        ChargeReconciliationResponse response = chargeReconciliationService.reconcile(EMAIL);

        // Then
        ChargeReconciliationResponse.Row row = response.rows().getFirst();
        assertThat(row.comparable()).isFalse();
        assertThat(row.entered()).isNull();
        assertThat(row.delta()).isNull();
        assertThat(row.note()).contains("no transaction");
        assertThat(response.comparableCount()).isZero();
        assertThat(response.totalDelta()).isZero();
    }

    /**
     * A computation that resolved nothing is a zero for a reason. It is excluded from the totals and
     * said so, rather than counted as the engine undercharging by the entered amount.
     */
    @Test
    void reconcile_whenNothingResolved_marksTheRowNotComparable() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL))
                .thenReturn(List.of(computed("txn-1", 0.0, ChargeResolution.NO_SCHEDULE)));
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(entered("txn-1", 20.00)));

        // When
        ChargeReconciliationResponse response = chargeReconciliationService.reconcile(EMAIL);

        // Then
        ChargeReconciliationResponse.Row row = response.rows().getFirst();
        assertThat(row.comparable()).isFalse();
        assertThat(row.delta()).isNull();
        assertThat(row.note()).contains("NO_SCHEDULE");
        assertThat(response.unresolvedCount()).isEqualTo(1);
        assertThat(response.totalDelta()).isZero();
    }

    @Test
    void reconcile_countsTransactionsTheEngineNeverSaw() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL))
                .thenReturn(List.of(computed("txn-1", 23.45, ChargeResolution.RESOLVED)));
        when(transactionRepository.findByEmail(EMAIL))
                .thenReturn(List.of(entered("txn-1", 20.00), entered("txn-2", 15.00), entered("txn-3", 18.00)));

        // When
        ChargeReconciliationResponse response = chargeReconciliationService.reconcile(EMAIL);

        // Then -- two of the three, deliberately not one: with a single reconciled row and a single
        // unseen one, counting the wrong side of the filter gives the same answer.
        assertThat(response.transactionsWithoutComputation()).isEqualTo(2);
    }

    @Test
    void reconcile_whenNothingWasRecorded_returnsAnEmptyReportRatherThanFailing() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL)).thenReturn(List.of());
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of());

        // When
        ChargeReconciliationResponse response = chargeReconciliationService.reconcile(EMAIL);

        // Then
        assertThat(response.rows()).isEmpty();
        assertThat(response.totalComputed()).isZero();
        assertThat(response.totalEntered()).isZero();
        assertThat(response.totalDelta()).isZero();
        assertThat(response.comparableCount()).isZero();
    }
}
