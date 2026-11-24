package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserChargeRepository extends JpaRepository<UserChargeEntity, String> {

    @Query("SELECT COUNT(u) > 0 FROM UserChargeEntity u JOIN u.amountByCode a"
            + " WHERE u.email = :email"
            + " AND ((:accountHolder IS NULL AND u.accountHolder IS NULL) OR u.accountHolder = :accountHolder)"
            + " AND u.brokerName = :brokerName AND u.stockCode = :stockCode"
            + " AND u.transactionDate = :transactionDate"
            + " AND KEY(a) = :chargeCode AND VALUE(a) > 0")
    boolean existsChargeForScripOnDate(
            @Param("email") String email, @Param("accountHolder") String accountHolder,
            @Param("brokerName") BrokerName brokerName, @Param("stockCode") String stockCode,
            @Param("transactionDate") LocalDate transactionDate, @Param("chargeCode") String chargeCode);

    @Query("SELECT COUNT(u) > 0 FROM UserChargeEntity u JOIN u.amountByCode a"
            + " WHERE u.email = :email"
            + " AND ((:accountHolder IS NULL AND u.accountHolder IS NULL) OR u.accountHolder = :accountHolder)"
            + " AND u.orderId = :orderId"
            + " AND KEY(a) = :chargeCode AND VALUE(a) > 0")
    boolean existsChargeForOrder(
            @Param("email") String email, @Param("accountHolder") String accountHolder,
            @Param("orderId") String orderId, @Param("chargeCode") String chargeCode);

    @Query("SELECT COUNT(u) > 0 FROM UserChargeEntity u JOIN u.amountByCode a"
            + " WHERE u.email = :email"
            + " AND ((:accountHolder IS NULL AND u.accountHolder IS NULL) OR u.accountHolder = :accountHolder)"
            + " AND u.brokerName = :brokerName AND u.transactionDate = :transactionDate"
            + " AND KEY(a) = :chargeCode AND VALUE(a) > 0")
    boolean existsChargeForDay(
            @Param("email") String email, @Param("accountHolder") String accountHolder,
            @Param("brokerName") BrokerName brokerName, @Param("transactionDate") LocalDate transactionDate,
            @Param("chargeCode") String chargeCode);

    Optional<UserChargeEntity> findByEmailAndTransactionId(String email, String transactionId);

    List<UserChargeEntity> findByEmailAndTransactionIdIn(String email, Collection<String> transactionIds);

    List<UserChargeEntity> findByEmailOrderByTransactionDateDesc(String email);

    Page<UserChargeEntity> findByEmail(String email, Pageable pageable);

    Page<UserChargeEntity> findByEmailAndAssetType(String email, AssetType assetType, Pageable pageable);

    Page<UserChargeEntity> findByEmailAndTransactionDateBetween(String email, LocalDate from, LocalDate to, Pageable pageable);

    Page<UserChargeEntity> findByEmailAndTransactionDateBetweenAndAssetType(
            String email, LocalDate from, LocalDate to, AssetType assetType, Pageable pageable);

    List<UserChargeEntity> findByEmailAndResolutionIn(String email, List<ChargeResolution> resolutions);

    @Transactional("postgresTransactionManager")
    void deleteByEmail(String email);
}
