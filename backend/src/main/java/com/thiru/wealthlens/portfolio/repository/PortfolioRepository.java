package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface PortfolioRepository extends MongoRepository<AssetEntity, String> {

    List<AssetEntity> findByEmail(String email);

    Optional<AssetEntity> findByEmailAndStockCodeAndBrokerNameAndAccountHolderAndTransactionDate(String email,
                                                                                                 String stockCode, BrokerName brokerName, String accountHolder, LocalDate transactionDate);

    List<AssetEntity> findByEmailAndStockCodeAndBrokerNameAndAccountHolderOrderByTransactionDate(String email,
                                                                                                 String stockCode, BrokerName brokerName, String accountHolder);

    @Query(
            value = "{ 'email': ?0, 'stock_code': ?1, 'broker_name': ?2, 'account_holder': ?3, 'transaction_date': { $lte: ?4 } }",
            sort = "{ 'transaction_date': 1 }"
    )
    List<AssetEntity> findEligibleHoldingsForSell(String email, String stockCode, BrokerName brokerName, String accountHolder, LocalDate transactionDate);

    List<AssetEntity> findByStockCodeAndTransactionDateBefore(String stockCode, LocalDate transactionDate);

    List<AssetEntity> findByEmailAndStockCodeAndTransactionDateBefore(String email, String stockCode, LocalDate transactionDate);

    List<AssetEntity> findByEmailAndStockCodeOrderByTransactionDate(String email, String stockCode);

    List<AssetEntity> findByEmailAndStockCodeIn(String email, Collection<String> stockCodes);

    List<AssetEntity> findByEmailAndAssetType(String email, AssetType assetType);

    List<AssetEntity> findByEmailAndTransactionDateBetween(String email, LocalDate startDate, LocalDate endDate);

    void deleteByEmail(String email);
}
