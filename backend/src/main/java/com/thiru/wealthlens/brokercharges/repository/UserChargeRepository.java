package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserChargeRepository extends MongoRepository<UserChargeEntity, String> {

    /**
     * Whether a charge with the given code has already been levied for this scrip, in this demat
     * account, on this date.
     *
     * <p>{@code accountHolder} is part of the key because depository charges are levied per demat
     * account: the same scrip sold on the same day under two holders incurs two separate debits.
     * Returns a boolean rather than the documents, because the caller only ever asks whether one
     * exists.
     */
    @Query(value = "{ 'email': ?0, 'account_holder': ?1, 'broker_name': ?2, 'stock_code': ?3,"
            + " 'transaction_date': ?4, 'amount_by_code.?5': { $gt: 0 } }", exists = true)
    boolean existsChargeForScripOnDate(String email, String accountHolder, BrokerName brokerName,
                                       String stockCode, LocalDate transactionDate, String chargeCode);

    /** Order-scoped deduplication, for charges capped per order however many trades fill it. */
    @Query(value = "{ 'email': ?0, 'account_holder': ?1, 'order_id': ?2,"
            + " 'amount_by_code.?3': { $gt: 0 } }", exists = true)
    boolean existsChargeForOrder(String email, String accountHolder, String orderId, String chargeCode);

    /** Day-scoped deduplication, across every scrip in an account. */
    @Query(value = "{ 'email': ?0, 'account_holder': ?1, 'broker_name': ?2,"
            + " 'transaction_date': ?3, 'amount_by_code.?4': { $gt: 0 } }", exists = true)
    boolean existsChargeForDay(String email, String accountHolder, BrokerName brokerName,
                               LocalDate transactionDate, String chargeCode);

    /** Recomputation replaces a transaction's row rather than appending, so a re-upload is safe. */
    Optional<UserChargeEntity> findByEmailAndTransactionId(String email, String transactionId);

    List<UserChargeEntity> findByEmailOrderByTransactionDateDesc(String email);

    List<UserChargeEntity> findByEmailAndTransactionDateBetween(String email, LocalDate from, LocalDate to);

    /** Rows whose charges could not be fully assessed. Drives the gaps report. */
    List<UserChargeEntity> findByEmailAndResolutionIn(String email, List<ChargeResolution> resolutions);

    /** Every row a given rate card produced, so a corrected card can find what it touched. */
    List<UserChargeEntity> findByScheduleId(String scheduleId);

    /**
     * The latest trade already recorded for a user, used to detect a batch arriving out of
     * sequence. Uploads are quarterly and chronological by convention, but the convention is not
     * enforced, so it is checked rather than assumed.
     */
    Optional<UserChargeEntity> findFirstByEmailOrderByTransactionDateDesc(String email);

    void deleteByEmail(String email);
}
