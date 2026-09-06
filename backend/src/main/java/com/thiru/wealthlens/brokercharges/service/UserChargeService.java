package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.engine.ChargeEngine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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

    /** The three reasons a stored number should not be taken at face value. */
    private static final List<ChargeResolution> UNRESOLVED = List.of(
            ChargeResolution.NO_SCHEDULE,
            ChargeResolution.NO_INSTRUMENT_PROFILE,
            ChargeResolution.PROVISIONAL);

    private final ChargeEngine chargeEngine;
    private final UserChargeRepository userChargeRepository;

    public ChargeComputation computeAndRecord(ChargeContext context) {
        return record(context, false);
    }

    /**
     * Prices a batch in date order, flagging it if it reaches back before what is already recorded.
     *
     * <p>Ordering is not a nicety. Deduplicated charges consult rows written earlier in the same
     * batch, and a holding period depends on the purchase having been seen before the redemption.
     *
     * <p>Uploads are meant to be chronological, but that is a process convention rather than
     * something the system enforces: someone will re-run a quarter or load a forgotten file. One
     * indexed query turns the resulting silent wrongness into a visible flag.
     */
    public List<ChargeComputation> computeAndRecordBatch(List<ChargeContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        List<ChargeContext> inOrder = contexts.stream()
                .sorted(Comparator.comparing(ChargeContext::transactionDate))
                .toList();

        boolean outOfSequence = reachesBackBeforeWhatIsRecorded(inOrder);
        if (outOfSequence) {
            log.warn("Charge batch for {} starts on {}, before the latest transaction already recorded;"
                            + " its computations are marked PROVISIONAL",
                    inOrder.getFirst().email(), inOrder.getFirst().transactionDate());
        }

        List<ChargeComputation> computations = new ArrayList<>();
        for (ChargeContext context : inOrder) {
            computations.add(record(context, outOfSequence));
        }
        return computations;
    }

    public List<UserChargeEntity> findHistory(String email) {
        return userChargeRepository.findByEmailOrderByTransactionDateDesc(email);
    }

    /** Rows whose charges could not be fully assessed. Drives the gaps report. */
    public List<UserChargeEntity> findGaps(String email) {
        return userChargeRepository.findByEmailAndResolutionIn(email, UNRESOLVED);
    }

    public UserChargeEntity findForTransaction(String email, String transactionId) {
        return userChargeRepository.findByEmailAndTransactionId(email, transactionId)
                .orElseThrow(() -> new BadRequestException(
                        "No charges recorded for transaction " + transactionId));
    }

    public void deleteByEmail(String email) {
        userChargeRepository.deleteByEmail(email);
    }

    private ChargeComputation record(ChargeContext context, boolean provisional) {
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
        row.setResolution(resolutionOf(computation, provisional));
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

    /**
     * A more specific gap is kept over {@code PROVISIONAL}. "No rate card on file" says more than
     * "this may be wrong", and either way the row appears in the gaps report.
     */
    private static ChargeResolution resolutionOf(ChargeComputation computation, boolean provisional) {
        if (provisional && computation.resolution() == ChargeResolution.RESOLVED) {
            return ChargeResolution.PROVISIONAL;
        }
        return computation.resolution();
    }

    private boolean reachesBackBeforeWhatIsRecorded(List<ChargeContext> inOrder) {
        Optional<UserChargeEntity> latest = userChargeRepository
                .findFirstByEmailOrderByTransactionDateDesc(inOrder.getFirst().email());

        if (latest.isEmpty() || latest.get().getTransactionDate() == null) {
            return false;
        }

        LocalDate earliestInBatch = inOrder.getFirst().transactionDate();
        return earliestInBatch.isBefore(latest.get().getTransactionDate());
    }
}
