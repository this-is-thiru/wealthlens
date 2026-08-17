package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserBrokerChargesRepository extends MongoRepository<UserBrokerCharges, String> {

    @Query("{ 'email': ?0, 'broker_name': ?1, 'stock_code': ?2, 'transaction_date': ?3, 'type': 'SELL' }")
    List<UserBrokerCharges> findTopSellTxnByBrokerNameAndStockCodeAndTransactionDate(
            String email, BrokerName brokerName, String stockCode, LocalDate transactionDate
    );

    void deleteByEmail(String email);

    List<UserBrokerCharges> findByEmail(String email);
}
