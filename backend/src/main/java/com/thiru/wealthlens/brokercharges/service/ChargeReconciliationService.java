package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.response.ChargeReconciliationResponse;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What the engine computed against what the user typed, per trade.
 *
 * <p>This is the report Phase B exists to produce. Nothing about shadow recording is worth doing if
 * the deltas cannot be read and acted on, and the reason this is a service rather than a projection
 * over two collections is that most of the work is deciding which rows may legitimately be
 * subtracted from each other.
 *
 * <p>Two kinds of row are reported but excluded from the totals, because subtracting them produces
 * a number that looks like a defect and is not:
 *
 * <ul>
 *   <li><b>The computation did not resolve.</b> A trade in a period with no rate card on file
 *       computes zero for a stated reason. Counting it would read as the engine undercharging by
 *       the entire entered amount.
 *   <li><b>The transaction is gone.</b> With no entered figure there is nothing to compare, and
 *       treating the absence as zero would read as the engine overcharging by its whole total.
 * </ul>
 *
 * <p>Sums are {@code BigDecimal}. A portfolio's worth of two-decimal figures added as doubles
 * drifts, and a totals row that disagrees with its own column by a paisa invites the reader to
 * distrust the column.
 */
@Service
@RequiredArgsConstructor
public class ChargeReconciliationService {

    private static final int PAISE_SCALE = 2;

    private final UserChargeRepository userChargeRepository;
    private final TransactionRepository transactionRepository;

    public ChargeReconciliationResponse reconcile(String email) {
        List<UserChargeEntity> computations = userChargeRepository.findByEmailOrderByTransactionDateDesc(email);
        // No null-id filter and no merge function: findByEmail returns documents, every document
        // carries its _id, and two documents in one collection cannot share one.
        Map<String, TransactionEntity> transactionsById = transactionRepository.findByEmail(email).stream()
                .collect(Collectors.toMap(TransactionEntity::getId, Function.identity()));

        List<ChargeReconciliationResponse.Row> rows = new ArrayList<>();
        Set<String> reconciledTransactionIds = new HashSet<>();
        BigDecimal totalComputed = BigDecimal.ZERO;
        BigDecimal totalEntered = BigDecimal.ZERO;
        int comparableCount = 0;
        int unresolvedCount = 0;

        for (UserChargeEntity computation : computations) {
            TransactionEntity transaction = transactionsById.get(computation.getTransactionId());
            if (transaction != null) {
                reconciledTransactionIds.add(transaction.getId());
            }

            boolean unresolved = computation.getResolution() != null && computation.getResolution().isUnresolved();
            if (unresolved) {
                unresolvedCount++;
            }

            rows.add(toRow(computation, transaction, unresolved));

            if (!unresolved && transaction != null) {
                comparableCount++;
                totalComputed = totalComputed.add(BigDecimal.valueOf(computation.getTotalCharges()));
                totalEntered = totalEntered.add(BigDecimal.valueOf(transaction.getBrokerCharges()));
            }
        }

        int transactionsWithoutComputation = (int) transactionsById.keySet().stream()
                .filter(id -> !reconciledTransactionIds.contains(id))
                .count();
        return new ChargeReconciliationResponse(
                List.copyOf(rows),
                scaled(totalComputed),
                scaled(totalEntered),
                scaled(totalComputed.subtract(totalEntered)),
                comparableCount,
                unresolvedCount,
                transactionsWithoutComputation);
    }

    private static ChargeReconciliationResponse.Row toRow(
            UserChargeEntity computation, TransactionEntity transaction, boolean unresolved) {

        Double entered = transaction == null ? null : transaction.getBrokerCharges();
        Double delta = null;
        String note = null;

        if (unresolved) {
            // The entered figure is still shown — it is the only figure this trade has — but the
            // difference is withheld, because the engine did not claim a number to differ by.
            note = "not compared: the computation resolved as " + computation.getResolution();
        } else if (transaction == null) {
            note = "not compared: no transaction " + computation.getTransactionId() + " on file";
        } else {
            delta = scaled(BigDecimal.valueOf(computation.getTotalCharges())
                    .subtract(BigDecimal.valueOf(transaction.getBrokerCharges())));
        }

        return new ChargeReconciliationResponse.Row(
                computation.getTransactionId(),
                computation.getStockCode(),
                computation.getTransactionDate(),
                computation.getBrokerName(),
                computation.getAssetType(),
                computation.getEvent(),
                computation.getResolution(),
                computation.getScheduleCode(),
                computation.getTotalCharges(),
                entered,
                delta,
                delta != null,
                note);
    }

    private static double scaled(BigDecimal amount) {
        return amount.setScale(PAISE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
