package com.thiru.wealthlens.brokercharges.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeBackfillReport;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pricing the transactions a user already has, so the reconciliation report has something to
 * reconcile.
 *
 * <p>Shadow recording only fires on trades that flow through the live path after the flag is turned
 * on, which leaves every historical transaction with no computed charge and the reconciliation
 * report with nothing in it. This is what fills that in — and it is the only route to a delta
 * against a figure a *user* actually typed rather than one a test invented.
 *
 * <p>The work that is not obvious is the FIFO reconstruction. A `TransactionEntity` for a sell does
 * not record the lots it consumed; the live path is handed them by `PortfolioService`'s walk over
 * open holdings, and that walk is destructive — the holdings are gone. Replaying the buys in date
 * order is the only way to recover them, and without them a `perLot` rule evaluates zero times, so
 * a redemption inside its exit-load window would be silently backfilled as free.
 */
@ExtendWith(MockitoExtension.class)
class ChargeBackfillServiceTest {

    private static final String EMAIL = "backfill@wealthlens.test";
    private static final String HOLDER = "self";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserChargeService userChargeService;

    @InjectMocks
    private ChargeBackfillService chargeBackfillService;

    private static TransactionEntity txn(String id, TransactionType type, String stockCode,
                                         double quantity, double price, LocalDate date) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail(EMAIL);
        transaction.setStockCode(stockCode);
        transaction.setExchangeName("NSE");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAccountHolder(HOLDER);
        transaction.setAccountType(AccountType.SELF);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setTransactionType(type);
        transaction.setQuantity(quantity);
        transaction.setPrice(price);
        transaction.setTransactionDate(date);
        transaction.setStatus(TransactionStatus.PROCESSED);
        return transaction;
    }

    private static ChargeComputation computed(double total) {
        return new ChargeComputation("sched-1", "ZERODHA_EQ_DELIVERY_2025_04", null,
                ChargeResolution.RESOLVED, List.of(), total);
    }

    @SuppressWarnings("unchecked")
    private List<ChargeContext> pricedContexts() {
        ArgumentCaptor<List<ChargeContext>> captor = ArgumentCaptor.forClass(List.class);
        verify(userChargeService).computeAndRecordBatch(captor.capture());
        return captor.getValue();
    }

    // ========================================
    // What gets priced
    // ========================================

    @Test
    void backfill_pricesEveryProcessedTransaction() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("t1", TransactionType.BUY, "RELIANCE", 100, 1000, LocalDate.of(2025, 6, 10)),
                txn("t2", TransactionType.BUY, "INFY", 50, 800, LocalDate.of(2025, 6, 11))));
        when(userChargeService.computeAndRecordBatch(any()))
                .thenReturn(List.of(computed(118.74), computed(60.10)));

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(pricedContexts()).extracting(ChargeContext::transactionId).containsExactly("t1", "t2");
        assertThat(report.transactionsRead()).isEqualTo(2);
        assertThat(report.priced()).isEqualTo(2);
        assertThat(report.totalComputed()).isEqualTo(178.84);
        assertThat(report.byResolution()).containsEntry(ChargeResolution.RESOLVED, 2);
    }

    /**
     * A temporary transaction has not been processed — a corporate action is blocking it — so it has
     * no charge yet. Pricing it would record a charge for a trade that has not happened.
     */
    @Test
    void backfill_skipsTransactionsThatWereNeverProcessed() {
        // Given
        TransactionEntity temporary = txn("t-temp", TransactionType.BUY, "RELIANCE", 100, 1000, LocalDate.of(2025, 6, 10));
        temporary.setStatus(TransactionStatus.TEMPORARY);
        TransactionEntity failed = txn("t-failed", TransactionType.BUY, "INFY", 10, 800, LocalDate.of(2025, 6, 11));
        failed.setStatus(TransactionStatus.FAILED);
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(temporary, failed));

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(report.transactionsRead()).isEqualTo(2);
        assertThat(report.skipped()).isEqualTo(2);
        assertThat(report.priced()).isZero();
        verifyNoInteractions(userChargeService);
    }

    /** Legacy rows predate the status field. Excluding them would silently skip a user's oldest trades. */
    @Test
    void backfill_pricesATransactionWhoseStatusWasNeverSet() {
        // Given
        TransactionEntity legacy = txn("t-legacy", TransactionType.BUY, "RELIANCE", 100, 1000, LocalDate.of(2021, 6, 10));
        legacy.setStatus(null);
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(legacy));
        when(userChargeService.computeAndRecordBatch(any())).thenReturn(List.of(computed(100.00)));

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(report.priced()).isEqualTo(1);
    }

    // ========================================
    // The mapping
    // ========================================

    @Test
    void backfill_mapsTheTransactionOntoTheChargeContext() {
        // Given
        TransactionEntity transaction = txn("t1", TransactionType.BUY, "RELIANCE", 100, 1000, LocalDate.of(2025, 6, 10));
        transaction.setOrderId("ord-9");
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(transaction));
        when(userChargeService.computeAndRecordBatch(any())).thenReturn(List.of(computed(118.74)));

        // When
        chargeBackfillService.backfill(EMAIL);

        // Then
        ChargeContext context = pricedContexts().getFirst();
        assertThat(context.email()).isEqualTo(EMAIL);
        assertThat(context.event()).isEqualTo(ChargeEvent.BUY);
        assertThat(context.orderId()).isEqualTo("ord-9");
        assertThat(context.accountHolder()).isEqualTo(HOLDER);
        assertThat(context.exchange()).isEqualTo("NSE");
        assertThat(context.segment()).isEqualTo(TradeSegment.DELIVERY);
        assertThat(context.quantity()).isEqualTo(100);
        assertThat(context.price()).isEqualTo(1000);
        assertThat(context.amount(AmountBasis.TURNOVER)).isEqualTo(100_000.0);
    }

    @Test
    void backfill_carriesTheCorporateActionThatProducedTheTrade() {
        // Given
        TransactionEntity bonus = txn("t-bonus", TransactionType.BUY, "RELIANCE", 10, 0, LocalDate.of(2025, 6, 10));
        bonus.setCorporateActionType(CorporateActionType.BONUS);
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(bonus));
        when(userChargeService.computeAndRecordBatch(any())).thenReturn(List.of(computed(0.0)));

        // When
        chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(pricedContexts().getFirst().corporateActionType()).isEqualTo(CorporateActionType.BONUS);
    }

    // ========================================
    // FIFO reconstruction — the reason this is not a loop over a repository
    // ========================================

    /**
     * The sell consumes the older lot first and takes only part of the newer one. Nothing in the
     * transaction document says so; it is recovered by replaying the buys in date order.
     */
    @Test
    void backfill_rebuildsTheLotsASellConsumed() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("b1", TransactionType.BUY, "RELIANCE", 40, 900, LocalDate.of(2025, 1, 5)),
                txn("b2", TransactionType.BUY, "RELIANCE", 60, 1100, LocalDate.of(2025, 5, 20)),
                txn("s1", TransactionType.SELL, "RELIANCE", 70, 1200, LocalDate.of(2025, 6, 10))));
        when(userChargeService.computeAndRecordBatch(any()))
                .thenReturn(List.of(computed(1), computed(1), computed(1)));

        // When
        chargeBackfillService.backfill(EMAIL);

        // Then
        ChargeContext sell = pricedContexts().stream()
                .filter(context -> context.transactionId().equals("s1")).findFirst().orElseThrow();
        assertThat(sell.lots()).hasSize(2);
        assertThat(sell.lots().get(0).quantity()).isEqualTo(40);
        assertThat(sell.lots().get(0).acquisitionDate()).isEqualTo(LocalDate.of(2025, 1, 5));
        assertThat(sell.lots().get(1).quantity()).isEqualTo(30);
        assertThat(sell.lots().get(1).acquisitionDate()).isEqualTo(LocalDate.of(2025, 5, 20));
    }

    /** The remainder of a partly consumed lot stays open for the next sell. */
    @Test
    void backfill_leavesThePartOfALotItDidNotConsume() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("b1", TransactionType.BUY, "RELIANCE", 100, 900, LocalDate.of(2025, 1, 5)),
                txn("s1", TransactionType.SELL, "RELIANCE", 30, 1200, LocalDate.of(2025, 6, 10)),
                txn("s2", TransactionType.SELL, "RELIANCE", 70, 1300, LocalDate.of(2025, 7, 10))));
        when(userChargeService.computeAndRecordBatch(any()))
                .thenReturn(List.of(computed(1), computed(1), computed(1)));

        // When
        chargeBackfillService.backfill(EMAIL);

        // Then
        List<ChargeContext> contexts = pricedContexts();
        ChargeContext second = contexts.stream()
                .filter(context -> context.transactionId().equals("s2")).findFirst().orElseThrow();
        assertThat(second.lots()).hasSize(1);
        assertThat(second.lots().getFirst().quantity()).isEqualTo(70);
        assertThat(second.lots().getFirst().acquisitionDate()).isEqualTo(LocalDate.of(2025, 1, 5));
    }

    /** Lots are held per scrip, per broker and per account holder — never pooled across them. */
    @Test
    void backfill_doesNotDrawALotFromADifferentScrip() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("b-infy", TransactionType.BUY, "INFY", 100, 800, LocalDate.of(2025, 1, 5)),
                txn("s-rel", TransactionType.SELL, "RELIANCE", 10, 1200, LocalDate.of(2025, 6, 10))));
        when(userChargeService.computeAndRecordBatch(any()))
                .thenReturn(List.of(computed(1), computed(1)));

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        ChargeContext sell = pricedContexts().stream()
                .filter(context -> context.transactionId().equals("s-rel")).findFirst().orElseThrow();
        assertThat(sell.lots()).isEmpty();
        assertThat(report.sellsWithNoLotsFound()).isEqualTo(1);
    }

    @Test
    void backfill_aBuyConsumesNoLots() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("b1", TransactionType.BUY, "RELIANCE", 100, 900, LocalDate.of(2025, 1, 5))));
        when(userChargeService.computeAndRecordBatch(any())).thenReturn(List.of(computed(1)));

        // When
        chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(pricedContexts().getFirst().lots()).isEmpty();
    }

    /**
     * Replay order decides which lots a sell draws, so it is date order — not whatever order the
     * database happened to return. A sell read before its own buy would find no lots at all.
     */
    @Test
    void backfill_replaysInDateOrderWhateverOrderTheDatabaseReturns() {
        // Given — the sell arrives first
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("s1", TransactionType.SELL, "RELIANCE", 40, 1200, LocalDate.of(2025, 6, 10)),
                txn("b1", TransactionType.BUY, "RELIANCE", 40, 900, LocalDate.of(2025, 1, 5))));
        when(userChargeService.computeAndRecordBatch(any()))
                .thenReturn(List.of(computed(1), computed(1)));

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        ChargeContext sell = pricedContexts().stream()
                .filter(context -> context.transactionId().equals("s1")).findFirst().orElseThrow();
        assertThat(sell.lots()).hasSize(1);
        assertThat(report.sellsWithNoLotsFound()).isZero();
    }

    // ========================================
    // Reporting
    // ========================================

    @Test
    void backfill_countsEachResolutionSeparately() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("t1", TransactionType.BUY, "RELIANCE", 100, 1000, LocalDate.of(2025, 6, 10)),
                txn("t2", TransactionType.BUY, "INFY", 10, 800, LocalDate.of(2019, 6, 10))));
        when(userChargeService.computeAndRecordBatch(any())).thenReturn(List.of(
                computed(118.74),
                ChargeComputation.empty(ChargeResolution.NO_SCHEDULE)));

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(report.byResolution())
                .containsEntry(ChargeResolution.RESOLVED, 1)
                .containsEntry(ChargeResolution.NO_SCHEDULE, 1);
        assertThat(report.totalComputed()).isEqualTo(118.74);
    }

    @Test
    void backfill_whenTheUserHasNoTransactions_reportsNothingRatherThanFailing() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of());

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(report.transactionsRead()).isZero();
        assertThat(report.priced()).isZero();
        assertThat(report.totalComputed()).isZero();
        verifyNoInteractions(userChargeService);
    }

    /** A quantity that never got written cannot be priced, and pricing it as zero would be a lie. */
    @Test
    void backfill_skipsATransactionWithNoQuantity() {
        // Given
        TransactionEntity broken = txn("t-broken", TransactionType.BUY, "RELIANCE", 0, 1000, LocalDate.of(2025, 6, 10));
        broken.setQuantity(null);
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(broken));

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(report.skipped()).isEqualTo(1);
        verifyNoInteractions(userChargeService);
    }

    /**
     * A sell that consumes a lot exactly must remove it, not leave a zero-quantity husk behind. The
     * husk is invisible until the next sell, which then draws a lot of nothing before reaching the
     * real one — and a zero-quantity lot inside an exit-load window prices at nothing while looking
     * like it was assessed.
     */
    @Test
    void backfill_whenASellConsumesALotExactly_leavesNoEmptyLotBehind() {
        // Given
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("b1", TransactionType.BUY, "RELIANCE", 40, 900, LocalDate.of(2025, 1, 5)),
                txn("b2", TransactionType.BUY, "RELIANCE", 60, 1100, LocalDate.of(2025, 2, 5)),
                txn("s1", TransactionType.SELL, "RELIANCE", 40, 1200, LocalDate.of(2025, 6, 10)),
                txn("s2", TransactionType.SELL, "RELIANCE", 60, 1300, LocalDate.of(2025, 7, 10))));
        when(userChargeService.computeAndRecordBatch(any()))
                .thenReturn(List.of(computed(1), computed(1), computed(1), computed(1)));

        // When
        chargeBackfillService.backfill(EMAIL);

        // Then — the second sell draws one lot, the second buy, and nothing else
        ChargeContext second = pricedContexts().stream()
                .filter(context -> context.transactionId().equals("s2")).findFirst().orElseThrow();
        assertThat(second.lots()).hasSize(1);
        assertThat(second.lots().getFirst().quantity()).isEqualTo(60);
        assertThat(second.lots().getFirst().acquisitionDate()).isEqualTo(LocalDate.of(2025, 2, 5));
    }

    /**
     * A sell for more than is open takes what there is. Asserting the shortfall rather than the ask
     * is what pins the remainder arithmetic: a lot that grew instead of shrank would satisfy the
     * quantity asked for and nobody would notice.
     */
    @Test
    void backfill_whenASellExceedsWhatIsOpen_takesOnlyTheRemainder() {
        // Given — 100 bought, 30 sold, then 80 asked for against the 70 that are left
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(
                txn("b1", TransactionType.BUY, "RELIANCE", 100, 900, LocalDate.of(2025, 1, 5)),
                txn("s1", TransactionType.SELL, "RELIANCE", 30, 1200, LocalDate.of(2025, 6, 10)),
                txn("s2", TransactionType.SELL, "RELIANCE", 80, 1300, LocalDate.of(2025, 7, 10))));
        when(userChargeService.computeAndRecordBatch(any()))
                .thenReturn(List.of(computed(1), computed(1), computed(1)));

        // When
        chargeBackfillService.backfill(EMAIL);

        // Then
        ChargeContext second = pricedContexts().stream()
                .filter(context -> context.transactionId().equals("s2")).findFirst().orElseThrow();
        assertThat(second.lots()).hasSize(1);
        assertThat(second.lots().getFirst().quantity()).isEqualTo(70);
    }

    /** A transaction that is neither a buy nor a sell has no side to price. */
    @Test
    void backfill_skipsATransactionWithNoTransactionType() {
        // Given
        TransactionEntity sideless = txn("t-sideless", TransactionType.BUY, "RELIANCE", 10, 1000, LocalDate.of(2025, 6, 10));
        sideless.setTransactionType(null);
        when(transactionRepository.findByEmail(EMAIL)).thenReturn(List.of(sideless));

        // When
        ChargeBackfillReport report = chargeBackfillService.backfill(EMAIL);

        // Then
        assertThat(report.skipped()).isEqualTo(1);
        verifyNoInteractions(userChargeService);
    }
}
