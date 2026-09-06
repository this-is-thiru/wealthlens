package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.engine.ChargeScheduleResolver;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishing and withdrawing rate cards.
 *
 * <h2>Publishing supersedes; it does not collide</h2>
 * A new card for a scope that already has an open one closes the incumbent at the day before the new
 * card starts, in the same transaction. The superseded implementation instead set an end date a
 * century out and then rejected any overlap, so publishing meant remembering to close the old card
 * first or watching the write throw (D6) — the kind of trap that ends with someone editing the
 * database by hand.
 *
 * <h2>Closing is not deactivating</h2>
 * Superseding sets {@code endDate} and never touches {@code status}. A transaction backdated into
 * the closed window must still find the card that was in force then, which is the normal case when a
 * past quarter is uploaded long after the rates changed, not an edge case (ADR-12).
 *
 * <p>Every write evicts the resolver's cache. It holds misses as well as hits, so without eviction a
 * newly published card stays invisible to the next trade priced.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class ChargeScheduleService {

    private final ChargeScheduleRepository chargeScheduleRepository;
    private final ChargeScheduleValidator chargeScheduleValidator;
    private final ChargeScheduleResolver chargeScheduleResolver;

    /**
     * Validates, closes any incumbent covering the same scope, and saves.
     *
     * @throws BadRequestException if the card is invalid, or would start before the card it replaces
     */
    public ChargeScheduleEntity publish(ChargeScheduleEntity schedule) {
        chargeScheduleValidator.validate(schedule);

        if (schedule.getStatus() == null) {
            // Status says whether a record is legitimate, not whether it is current. Leaving it
            // unset would be ambiguous to every later reader.
            schedule.setStatus(EntityStatus.ACTIVE);
        }

        supersedeIncumbent(schedule);

        ChargeScheduleEntity saved = chargeScheduleRepository.save(schedule);
        chargeScheduleResolver.evictAll();
        log.info("Published charge schedule {} for {} from {}",
                saved.getScheduleCode(), saved.getBrokerName(), saved.getStartDate());
        return saved;
    }

    public ChargeScheduleEntity findByCode(String scheduleCode) {
        return chargeScheduleRepository.findByScheduleCode(scheduleCode)
                .orElseThrow(() -> new BadRequestException("No charge schedule with code " + scheduleCode));
    }

    /** Withdraws a card without replacing it. The window closes; the record stays usable for past dates. */
    public ChargeScheduleEntity close(String scheduleCode, LocalDate endDate) {
        ChargeScheduleEntity schedule = findByCode(scheduleCode);

        if (endDate.isBefore(schedule.getStartDate())) {
            throw new BadRequestException("Cannot close charge schedule " + scheduleCode + " on " + endDate
                    + ": that end date precedes its start date of " + schedule.getStartDate());
        }

        schedule.setEndDate(endDate);
        ChargeScheduleEntity saved = chargeScheduleRepository.save(schedule);
        chargeScheduleResolver.evictAll();
        return saved;
    }

    public List<ChargeScheduleEntity> findByBroker(BrokerName brokerName) {
        return chargeScheduleRepository.findByBrokerNameOrderByStartDateDesc(brokerName);
    }

    /** Cards whose rates no human has checked against the broker's published page. */
    public List<ChargeScheduleEntity> findUnverified() {
        return chargeScheduleRepository.findByVerifiedOnIsNull();
    }

    private void supersedeIncumbent(ChargeScheduleEntity schedule) {
        Optional<ChargeScheduleEntity> found = chargeScheduleRepository.findOpenScheduleForScope(
                schedule.getBrokerName(),
                name(schedule.getAssetType()),
                name(schedule.getSegment()),
                schedule.getExchange(),
                schedule.getPlanCode());

        if (found.isEmpty()) {
            return;
        }

        ChargeScheduleEntity incumbent = found.get();
        if (incumbent.getScheduleCode().equals(schedule.getScheduleCode())) {
            // Republishing a correction to the card that is already open. Superseding it would close
            // it the day before its own start and then collide on its unique code.
            return;
        }

        LocalDate closeOn = schedule.getStartDate().minusDays(1);
        if (closeOn.isBefore(incumbent.getStartDate())) {
            throw new BadRequestException("Charge schedule " + schedule.getScheduleCode() + " starts on "
                    + schedule.getStartDate() + ", which is not after the open schedule "
                    + incumbent.getScheduleCode() + " starting on " + incumbent.getStartDate()
                    + ". Publishing it would end that card before it began.");
        }

        incumbent.setEndDate(closeOn);
        chargeScheduleRepository.save(incumbent);
        log.info("Superseded charge schedule {}: closed on {} by {}",
                incumbent.getScheduleCode(), closeOn, schedule.getScheduleCode());
    }

    /** Scope dimensions are stored as strings, and an unset one must stay null to mean "any". */
    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
