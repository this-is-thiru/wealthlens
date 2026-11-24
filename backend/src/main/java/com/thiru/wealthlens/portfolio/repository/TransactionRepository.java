package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String>, JpaSpecificationExecutor<TransactionEntity> {

    List<TransactionEntity> findByEmail(String email);

    Page<TransactionEntity> findByEmail(String email, Pageable pageable);

    List<TransactionEntity> findByEmailAndStockCodeAndTransactionDateBefore(String email, String stockCode, LocalDate transactionDate);

    List<TransactionEntity> findByStockCodeAndTransactionDateBeforeOrderByTransactionDateDesc(String stockCode, LocalDate transactionDate);

    List<TransactionEntity> findByEmailAndStockCodeAndBrokerNameAndTransactionDateBeforeOrderByTransactionDateDesc(String email, String stockCode, BrokerName brokerName, LocalDate transactionDate);

    @Transactional("postgresTransactionManager")
    void deleteByEmail(String email);

    Optional<TransactionEntity> findByEmailAndSourceTempTransactionId(String email, String sourceTempTransactionId);

    List<TransactionEntity> findByEmailAndStatus(String email, TransactionStatus status);

    List<TransactionEntity> findByEmailAndStatusAndStockCodeAndAssetTypeAndTransactionDateBefore(
            String email, TransactionStatus status, String stockCode, AssetType assetType, LocalDate date);

    List<TransactionEntity> findByEmailAndStatusAndStockCodeAndAssetTypeAndTransactionDateAfterOrderByTransactionDateAsc(
            String email, TransactionStatus status, String stockCode, AssetType assetType, LocalDate date);

    @Transactional("postgresTransactionManager")
    void deleteByEmailAndStatus(String email, TransactionStatus status);

    Optional<TransactionEntity> findByEmailAndIdempotencyKey(String email, String idempotencyKey);

    Optional<TransactionEntity> findFirstByEmailAndTradeFingerprintAndSubmittedAtAfterOrderBySubmittedAtDesc(
            String email, String tradeFingerprint, LocalDateTime submittedAfter);

    List<TransactionEntity> findByEmailAndTransactionTypeOrderByTransactionDateAsc(String email, TransactionType transactionType);

    @Query("SELECT DISTINCT t.email FROM TransactionEntity t")
    List<String> findDistinctEmails();
}
