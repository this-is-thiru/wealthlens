package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeBackfillReport;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prices the transactions a user already has, so the reconciliation report has something to
 * reconcile.
 *
 * <p>Shadow recording only fires on trades flowing through the live path *after* the flag is turned
 * on. Every transaction older than that has no computed charge, which leaves the reconciliation
 * report empty and `transactionsWithoutComputation` equal to the user's whole history. This fills it
 * in — and it is the only route to a delta against a figure a **user** actually typed, rather than
 * one a test invented.
 *
 * <p>Safe to re-run: {@code UserChargeService} keys a stored row on
 * {@code {email, transactionId}} and replaces it, so a second pass reprices rather than duplicates.
 *
 * <h2>The FIFO reconstruction</h2>
 *
 * <p>This is the part that is not a loop over a repository. A {@code TransactionEntity} for a sell
 * does not record the lots it consumed — the live path is handed them by {@code PortfolioService}'s
 * walk over open holdings, and that walk is destructive, so by now the holdings are gone or changed.
 * Replaying the buys in date order is the only way to recover them.
 *
 * <p>It matters because a {@code perLot} rule evaluates once per lot and not at all when there are
 * none. A mutual fund redeemed inside its exit-load window, backfilled without lots, would be
 * recorded as free — the precise failure this design exists to prevent, and one that would look like
 * the engine disagreeing with the user rather than like missing input.
 *
 * <p>Lots are held per scrip, per broker and per account holder, never pooled: a demat account is
 * the unit a holding actually lives in, and pooling would draw a lot from a position the user never
 * touched.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class ChargeBackfillService {

    private static final int PAISE_SCALE = 2;

    /** Cash-segment instruments trade in units of one; derivatives do not reach this path. */
    private static final int CASH_SEGMENT_LOT_SIZE = 1;

    /** Everything recorded before Chunk 10b carries no segment, and all of it was delivery. */
    private static final TradeSegment SEGMENT_BEFORE_IT_WAS_RECORDED = TradeSegment.DELIVERY;

    private final TransactionRepository transactionRepository;
    private final UserChargeService userChargeService;
    private final ChargeEngineProperties chargeEngineProperties;

    public ChargeBackfillReport backfill(String email) {
        // Writes a row per trade across a whole history. A disabled engine must not be doing that.
        ChargeEngineSwitch.requireEnabled(chargeEngineProperties);

        List<TransactionEntity> transactions = transactionRepository.findByEmail(email);

        // Date order first: replay order decides which lots a sell draws, and a sell read before its
        // own buy would find none. The database's order is not a guarantee.
        List<TransactionEntity> inOrder = transactions.stream()
                .sorted(Comparator.comparing(TransactionEntity::getTransactionDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<String, Deque<OpenLot>> openLots = new HashMap<>();
        List<ChargeContext> contexts = new ArrayList<>();
        int skipped = 0;
        int sellsWithNoLotsFound = 0;

        for (TransactionEntity transaction : inOrder) {
            if (!isPriceable(transaction)) {
                skipped++;
                continue;
            }

            ChargeEvent event = toEvent(transaction.getTransactionType());
            List<LotSlice> lots = List.of();
            if (event == ChargeEvent.SELL) {
                lots = consume(openLots, transaction);
                if (lots.isEmpty()) {
                    sellsWithNoLotsFound++;
                    log.warn("Backfill found no open lots for sell {} of {} on {}; any holding-period"
                                    + " charge on it will price at zero",
                            transaction.getId(), transaction.getStockCode(), transaction.getTransactionDate());
                }
            } else {
                open(openLots, transaction);
            }

            contexts.add(toContext(email, transaction, event, lots));
        }

        if (contexts.isEmpty()) {
            return new ChargeBackfillReport(transactions.size(), 0, skipped, sellsWithNoLotsFound, 0.0, Map.of());
        }

        List<ChargeComputation> computations = userChargeService.computeAndRecordBatch(contexts);

        Map<ChargeResolution, Integer> byResolution = new EnumMap<>(ChargeResolution.class);
        BigDecimal totalComputed = BigDecimal.ZERO;
        for (ChargeComputation computation : computations) {
            byResolution.merge(computation.resolution(), 1, Integer::sum);
            totalComputed = totalComputed.add(BigDecimal.valueOf(computation.total()));
        }

        log.info("Backfilled {} of {} transactions for {}: {} priced, {} skipped, {} sells with no lots found",
                computations.size(), transactions.size(), email, computations.size(), skipped, sellsWithNoLotsFound);

        return new ChargeBackfillReport(
                transactions.size(),
                computations.size(),
                skipped,
                sellsWithNoLotsFound,
                totalComputed.setScale(PAISE_SCALE, RoundingMode.HALF_UP).doubleValue(),
                byResolution);
    }

    /**
     * A transaction that was never processed has no charge yet — a corporate action is blocking it,
     * or it failed — and pricing one would record a charge for a trade that has not happened. A null
     * status is *not* excluded: it predates the field, and dropping those would silently skip a
     * user's oldest trades, which are exactly the ones a backfill exists for.
     */
    private static boolean isPriceable(TransactionEntity transaction) {
        if (transaction.getStatus() == TransactionStatus.TEMPORARY
                || transaction.getStatus() == TransactionStatus.FAILED) {
            return false;
        }
        if (transaction.getTransactionDate() == null || transaction.getQuantity() == null) {
            return false;
        }
        return toEvent(transaction.getTransactionType()) != null;
    }

    private static ChargeEvent toEvent(TransactionType transactionType) {
        if (transactionType == null) {
            return null;
        }
        return switch (transactionType) {
            case BUY -> ChargeEvent.BUY;
            case SELL -> ChargeEvent.SELL;
        };
    }

    /** A holding lives in one demat account, under one broker, for one scrip. Never pooled. */
    private static String positionKey(TransactionEntity transaction) {
        return transaction.getStockCode() + "|" + transaction.getBrokerName() + "|" + transaction.getAccountHolder();
    }

    private static void open(Map<String, Deque<OpenLot>> openLots, TransactionEntity buy) {
        openLots.computeIfAbsent(positionKey(buy), key -> new ArrayDeque<>())
                .addLast(new OpenLot(buy.getQuantity(), buy.getTransactionDate(), buy.getPrice()));
    }

    /**
     * Draws the sold quantity from the oldest lots first, leaving any remainder of a partly consumed
     * lot open for the next sell.
     */
    private static List<LotSlice> consume(Map<String, Deque<OpenLot>> openLots, TransactionEntity sell) {
        Deque<OpenLot> lots = openLots.get(positionKey(sell));
        if (lots == null || lots.isEmpty()) {
            return List.of();
        }

        List<LotSlice> consumed = new ArrayList<>();
        double remaining = sell.getQuantity();
        while (remaining > 0 && !lots.isEmpty()) {
            OpenLot lot = lots.peekFirst();
            if (lot.quantity <= remaining) {
                consumed.add(new LotSlice(lot.quantity, lot.acquisitionDate, lot.price));
                remaining -= lot.quantity;
                lots.removeFirst();
            } else {
                consumed.add(new LotSlice(remaining, lot.acquisitionDate, lot.price));
                lot.quantity -= remaining;
                remaining = 0;
            }
        }
        return consumed;
    }

    private static ChargeContext toContext(String email, TransactionEntity transaction,
                                           ChargeEvent event, List<LotSlice> lots) {
        double quantity = transaction.getQuantity();
        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, transaction.getPrice() * quantity);

        return new ChargeContext(
                email,
                transaction.getId(),
                transaction.getOrderId(),
                transaction.getStockCode(),
                transaction.getAccountHolder(),
                transaction.getBrokerName(),
                transaction.getAssetType(),
                segmentOf(transaction),
                transaction.getExchangeName(),
                null,
                event,
                transaction.getTransactionDate(),
                transaction.getCorporateActionType(),
                quantity,
                transaction.getPrice(),
                CASH_SEGMENT_LOT_SIZE,
                baseAmounts,
                lots,
                new HashMap<>());
    }

    /** One open buy lot, mutable because a sell may consume part of it and leave the rest. */
    private static final class OpenLot {
        private double quantity;
        private final LocalDate acquisitionDate;
        private final double price;

        private OpenLot(double quantity, LocalDate acquisitionDate, double price) {
            this.quantity = quantity;
            this.acquisitionDate = acquisitionDate;
            this.price = price;
        }
    }

    private static TradeSegment segmentOf(TransactionEntity transaction) {
        return transaction.getSegment() == null ? SEGMENT_BEFORE_IT_WAS_RECORDED : transaction.getSegment();
    }
}
