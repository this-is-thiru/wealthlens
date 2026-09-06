package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeAccountRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Annual maintenance, billed per demat account.
 *
 * <p>Unlike every other charge here this one is not triggered by a trade: it is a cycle run over
 * accounts by a clock. That is why it is a service rather than another calculator — the engine
 * prices an event, and this decides which events there are.
 *
 * <h2>Re-running a cycle is a no-op</h2>
 * Someone will run it twice: a retried job, a reprocessed quarter. The account's
 * {@code lastBilledThrough} watermark is what makes the second run do nothing, and the synthetic
 * transaction id is stable per account and period so that even a forced recomputation rewrites the
 * same charge row rather than appending a second.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class AmcChargeService {

    private final ChargeAccountRepository chargeAccountRepository;
    private final UserChargeService userChargeService;

    /**
     * Bills every account of the given frequency not yet covered through the given date.
     *
     * @return the accounts actually billed
     */
    public List<ChargeAccountEntity> runCycle(AmcChargeFrequency frequency, LocalDate billedThrough) {
        List<ChargeAccountEntity> billed = new ArrayList<>();

        for (ChargeAccountEntity account : chargeAccountRepository.findDueForAmc(frequency, billedThrough)) {
            if (alreadyCovered(account, billedThrough)) {
                // The repository query excludes these; the guard is restated because a duplicate
                // charge is indistinguishable from a legitimate one once written.
                continue;
            }
            bill(account, billedThrough).ifPresent(billed::add);
        }
        return billed;
    }

    private static boolean alreadyCovered(ChargeAccountEntity account, LocalDate billedThrough) {
        return account.getLastBilledThrough() != null
                && !account.getLastBilledThrough().isBefore(billedThrough);
    }

    private Optional<ChargeAccountEntity> bill(ChargeAccountEntity account, LocalDate billedThrough) {
        LocalDate periodFrom = periodStart(account);
        ChargeComputation computation = userChargeService.computeAndRecord(
                cycleContext(account, billedThrough));

        if (computation.resolution() != ChargeResolution.RESOLVED) {
            // Charging nothing because no card was on file is a gap, not a free account. Advancing
            // the watermark would mean the period is never billed once the card is added.
            log.warn("Maintenance charge for demat account {} through {} resolved as {}; leaving the"
                            + " billing watermark where it was so the period can be billed later",
                    account.getDematAccountId(), billedThrough, computation.resolution());
            return Optional.empty();
        }

        UserChargeEntity row = userChargeService.findForTransaction(
                account.getEmail(), transactionId(account, billedThrough));

        account.getBillingEvents().add(new ChargeAccountEntity.BillingEvent(
                row.getId(), periodFrom, billedThrough, billedThrough, computation.total()));
        account.setLastBilledThrough(billedThrough);

        return Optional.of(chargeAccountRepository.save(account));
    }

    /** The day after the last period ended, so consecutive cycles neither gap nor overlap. */
    private static LocalDate periodStart(ChargeAccountEntity account) {
        return account.getLastBilledThrough() == null
                ? account.getOpenedOn()
                : account.getLastBilledThrough().plusDays(1);
    }

    /**
     * The account as a chargeable event.
     *
     * <p>No scrip, no quantity, no turnover — so a rate card scoped to an asset type cannot apply,
     * and the maintenance card has to leave that dimension unset to be resolvable at all.
     */
    private static ChargeContext cycleContext(ChargeAccountEntity account, LocalDate billedThrough) {
        return new ChargeContext(
                account.getEmail(),
                transactionId(account, billedThrough),
                null,
                null,
                account.getAccountHolder(),
                account.getBrokerName(),
                null,
                null,
                null,
                account.getPlanCode(),
                ChargeEvent.AMC_CYCLE,
                billedThrough,
                null,
                0,
                0,
                1,
                Map.of(AmountBasis.TURNOVER, 0.0),
                List.of(),
                Map.of());
    }

    /** Stable per account and period, so a recomputation replaces rather than appends. */
    private static String transactionId(ChargeAccountEntity account, LocalDate billedThrough) {
        return "AMC-" + account.getDematAccountId() + "-" + billedThrough;
    }
}
