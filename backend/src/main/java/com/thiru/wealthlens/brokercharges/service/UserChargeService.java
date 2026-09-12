package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.engine.ChargeEngine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes a charge and records what was computed.
 *
 * <p>The stored row is the source of truth. A contract note is reconstructible from one document,
 * and the profit-and-loss charge hierarchy is a projection of these rows rather than the reverse —
 * a recomputation rebuilds a period from them instead of applying deltas, because a contribution
 * already folded into a sum cannot be reliably subtracted.
 *
 * <h2>A row is written even when nothing is charged</h2>
 * Backfilling several years crosses periods with no rate card on file. A warning in a log scrolls
 * away long before anyone notices; a row carrying its reason turns up in the gaps report.
 *
 * <h2>Recomputation replaces rather than appends</h2>
 * Rows are keyed on {@code {email, transactionId}}, so re-running a quarter to correct a file
 * rewrites the same document instead of charging twice.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class UserChargeService {


    private final ChargeEngine chargeEngine;
    private final UserChargeRepository userChargeRepository;

    public ChargeComputation computeAndRecord(ChargeContext context) {
        return record(context);
    }

    public List<UserChargeEntity> findHistory(String email) {
        return userChargeRepository.findByEmailOrderByTransactionDateDesc(email);
    }

    /**
     * Charge history, optionally narrowed.
     *
     * <p>The date range is answered by the database and the asset type in memory, which is
     * deliberate rather than lazy: {@code {email, transaction_date}} is indexed, so the range is the
     * predicate that does the selective work, and what survives it is one period of one user's
     * charges — small enough that a second index earns nothing.
     *
     * @param from        inclusive, or null for no range. Both bounds or neither
     * @param to          inclusive, or null for no range
     * @param assetType   null to keep every asset type
     * @throws BadRequestException if only one bound is supplied, or the range runs backwards.
     *                             A half-open range reads as "since April" to one caller and "up to
     *                             April" to another, and guessing wrong looks like missing charges
     */
    public List<UserChargeEntity> findHistory(String email, LocalDate from, LocalDate to, AssetType assetType) {
        if ((from == null) != (to == null)) {
            throw new BadRequestException("A date range needs both from and to, or neither");
        }
        if (from != null && from.isAfter(to)) {
            throw new BadRequestException("Date range starts after it ends: " + from + " is after " + to);
        }

        List<UserChargeEntity> charges = from == null
                ? userChargeRepository.findByEmailOrderByTransactionDateDesc(email)
                : userChargeRepository.findByEmailAndTransactionDateBetween(email, from, to);

        if (assetType == null) {
            return charges;
        }
        return charges.stream().filter(charge -> charge.getAssetType() == assetType).toList();
    }

    /** Rows whose charges could not be fully assessed. Drives the gaps report. */
    public List<UserChargeEntity> findGaps(String email) {
        return userChargeRepository.findByEmailAndResolutionIn(email, ChargeResolution.UNRESOLVED);
    }

    /**
     * The recorded charges for a transaction, or empty when there are none.
     *
     * <p>Distinct from {@link #findForTransaction} deliberately. A buy made before the engine was
     * switched on has no charge row, and ADR-32 settles that it never will — so a sell that matches
     * against such a lot must record what it can and carry on, not fail.
     */
    public Optional<UserChargeEntity> findOptionalForTransaction(String email, String transactionId) {
        return userChargeRepository.findByEmailAndTransactionId(email, transactionId);
    }

    public UserChargeEntity findForTransaction(String email, String transactionId) {
        return userChargeRepository.findByEmailAndTransactionId(email, transactionId)
                .orElseThrow(() -> new BadRequestException(
                        "No charges recorded for transaction " + transactionId));
    }

    public void deleteByEmail(String email) {
        userChargeRepository.deleteByEmail(email);
    }

    private ChargeComputation record(ChargeContext context) {
        ChargeComputation computation = chargeEngine.compute(context);

        UserChargeEntity row = userChargeRepository
                .findByEmailAndTransactionId(context.email(), context.transactionId())
                .orElseGet(UserChargeEntity::new);

        row.setEmail(context.email());
        row.setAccountHolder(context.accountHolder());
        row.setBrokerName(context.brokerName());
        row.setAssetType(context.assetType());
        row.setSegment(context.segment());
        row.setExchange(context.exchange());
        row.setStockCode(context.stockCode());
        row.setTransactionId(context.transactionId());
        row.setOrderId(context.orderId());
        row.setEvent(context.event());
        row.setTransactionDate(context.transactionDate());
        row.setComputedOn(TLocalDateTime.now());
        row.setResolution(computation.resolution());
        row.setScheduleId(computation.scheduleId());
        row.setScheduleCode(computation.scheduleCode());
        row.setInstrumentId(computation.instrumentId());
        row.setTurnover(context.amount(AmountBasis.TURNOVER));
        row.setQuantity(context.quantity());
        row.setLines(new ArrayList<>(computation.lines()));
        row.setAmountByCode(computation.amountByCode());
        row.setTotalCharges(computation.total());

        userChargeRepository.save(row);
        return computation;
    }
}
