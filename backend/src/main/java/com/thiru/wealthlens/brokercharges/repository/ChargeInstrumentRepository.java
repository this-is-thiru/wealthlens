package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ChargeInstrumentRepository extends MongoRepository<ChargeInstrumentEntity, String> {

    /**
     * The instrument's profile as it stood on the given date.
     *
     * <p>Same validity semantics as {@link ChargeScheduleRepository#findCandidates}: an asset
     * management company revising an exit load closes the previous profile's window, and a
     * redemption backdated into that window must use the load in force then.
     */
    @Query("{ 'stock_code': ?0, 'status': { $ne: 'INACTIVE' }, 'start_date': { $lte: ?1 },"
            + " $or: [ { 'end_date': null }, { 'end_date': { $gte: ?1 } } ] }")
    List<ChargeInstrumentEntity> findCandidates(String stockCode, LocalDate transactionDate);

    @Query("{ 'stock_code': ?0, 'status': { $ne: 'INACTIVE' }, 'end_date': null }")
    Optional<ChargeInstrumentEntity> findOpenProfile(String stockCode);

    Optional<ChargeInstrumentEntity> findByIsin(String isin);

    /**
     * A profile's identity for seeding purposes.
     *
     * <p>A profile has no code of its own, so the pair that a shipped file cannot change without
     * becoming a different profile is the scheme and the date its version took effect — the
     * counterpart of {@code scheduleCode} on a rate card.
     */
    Optional<ChargeInstrumentEntity> findByStockCodeAndStartDate(String stockCode, LocalDate startDate);
}
