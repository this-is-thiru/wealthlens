package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

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
