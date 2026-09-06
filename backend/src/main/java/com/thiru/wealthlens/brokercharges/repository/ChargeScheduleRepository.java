package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ChargeScheduleRepository extends MongoRepository<ChargeScheduleEntity, String> {

    /**
     * Every rate card for this broker whose validity window contains the given date.
     *
     * <p>Ranking by specificity is the resolver's job, so this deliberately returns all candidates
     * rather than guessing.
     *
     * <h2>Why the status predicate is {@code $ne: INACTIVE}</h2>
     * A superseded card must still price a transaction backdated into its own window — uploading a
     * past quarter long after the rates changed is the normal case, not an edge case. Filtering on
     * {@code status == ACTIVE} would find nothing and silently charge zero. {@code INACTIVE} means
     * the card was entered in error and is unusable for any date; expiry is expressed by
     * {@code end_date}, never by status. The negated form also survives a future maintainer setting
     * {@code SUPERSEDED} in the belief that it is correct.
     */
    @Query("{ 'broker_name': ?0, 'status': { $ne: 'INACTIVE' }, 'start_date': { $lte: ?1 },"
            + " $or: [ { 'end_date': null }, { 'end_date': { $gte: ?1 } } ] }")
    List<ChargeScheduleEntity> findCandidates(BrokerName brokerName, LocalDate transactionDate);

    Optional<ChargeScheduleEntity> findByScheduleCode(String scheduleCode);

    /**
     * The open-ended card for a scope, if there is one. Publishing a new card closes this one by
     * setting its end date, rather than deactivating it.
     */
    @Query("{ 'broker_name': ?0, 'asset_type': ?1, 'segment': ?2, 'exchange': ?3, 'plan_code': ?4,"
            + " 'status': { $ne: 'INACTIVE' }, 'end_date': null }")
    Optional<ChargeScheduleEntity> findOpenScheduleForScope(
            BrokerName brokerName, String assetType, String segment, String exchange, String planCode);

    /** Every card on file for a broker, newest window first. Drives the admin listing. */
    List<ChargeScheduleEntity> findByBrokerNameOrderByStartDateDesc(BrokerName brokerName);

    List<ChargeScheduleEntity> findByVerifiedOnIsNull();
}
