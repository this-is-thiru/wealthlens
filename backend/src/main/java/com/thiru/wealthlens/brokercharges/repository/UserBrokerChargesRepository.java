package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * <b>Superseded.</b> Storage for the superseded per-user charges.
 *
 * <p>Replaced by {@code UserChargeRepository}.
 *
 * <p><b>Do not delete yet.</b> Still reached by live code, and removal waits on human testing of
 * the charges engine — see {@code docs/charges-engine/implementation-checklist.md}, Chunk 11.
 *
 * <p>Plain {@code @Deprecated} rather than {@code forRemoval = true} on purpose. A removal warning
 * is <em>not</em> suppressed at a deprecated use site, so seventeen interlinked classes would warn
 * about each other and bury the only signal worth having. An ordinary deprecation warning is
 * suppressed inside deprecated code, which leaves the build reporting exactly the <b>live</b>
 * callers still to be migrated — and that list reaching zero is the precondition for deleting any
 * of this.
 */
@Deprecated
public interface UserBrokerChargesRepository extends MongoRepository<UserBrokerCharges, String> {

    /**
     * Whether a depository charge has already been levied for this scrip, in this demat account,
     * on this date.
     *
     * <p>{@code accountHolder} is part of the key because a DP charge is levied per demat account.
     * A user tracking holdings for more than one person who sells the same scrip on the same day in
     * two accounts incurs two separate debits, and so two charges.
     */
    @Query(value = "{ 'email': ?0, 'account_holder': ?1, 'broker_name': ?2, 'stock_code': ?3, "
            + "'transaction_date': ?4, 'type': 'SELL', 'dp_charges': { $gt: 0 } }", exists = true)
    boolean existsSellWithDpChargeOnDate(
            String email, String accountHolder, BrokerName brokerName, String stockCode, LocalDate transactionDate
    );

    void deleteByEmail(String email);

    List<UserBrokerCharges> findByEmail(String email);
}
