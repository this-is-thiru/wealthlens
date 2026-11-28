package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PortfolioRepository extends JpaRepository<AssetEntity, String>, JpaSpecificationExecutor<AssetEntity> {

    String OLDEST_FIRST = "ORDER BY a.auditMetadata.createdAt ASC NULLS FIRST, a.id ASC";

    /**
     * Every lot oldest first — creation time, the one lot stored before auditing first, then id — so each holding a view
     * combines shows its oldest lot's own fields and the list keeps one order (W-419, D-1002); with no order the
     * store's own would decide, which no column records.
     */
    @Query("SELECT a FROM AssetEntity a WHERE a.email = :email " + OLDEST_FIRST)
    List<AssetEntity> findByEmail(@Param("email") String email);

    /**
     * The oldest lot of a stock bought on a day in a segment — the one V1's buy merges into; V2 may have bought several
     * (W-272). A buy of another segment that day is a lot of its own (W-296).
     *
     * <p>Ordered by creation time, not id (W-337e, D-949): the id is a random UUID (W-337a's convention), which
     * carries no temporal order.
     */
    Optional<AssetEntity> findFirstByEmailAndStockCodeAndBrokerNameAndAccountHolderAndSegmentAndTransactionDateOrderByAuditMetadataCreatedAtAsc(
            String email, String stockCode, BrokerName brokerName, String accountHolder, TradeSegment segment,
            LocalDate transactionDate);

    @Query("SELECT a FROM AssetEntity a WHERE a.email = :email AND a.stockCode = :stockCode"
            + " AND a.brokerName = :brokerName AND a.accountHolder = :accountHolder"
            + " AND a.transactionDate <= :transactionDate ORDER BY a.transactionDate ASC")
    List<AssetEntity> findEligibleHoldingsForSell(@Param("email") String email, @Param("stockCode") String stockCode,
            @Param("brokerName") BrokerName brokerName, @Param("accountHolder") String accountHolder,
            @Param("transactionDate") LocalDate transactionDate);

    List<AssetEntity> findByStockCodeAndTransactionDateBefore(String stockCode, LocalDate transactionDate);

    List<AssetEntity> findByEmailAndStockCodeAndTransactionDateBefore(String email, String stockCode, LocalDate transactionDate);

    /**
     * A stock's lots by trade date, then oldest first as {@link #findByEmail}, so lots sharing a trade date — a bonus
     * allotted on two brokers, say — come back in one order (W-422, D-1011); by trade date alone they came back in
     * whatever order the store kept.
     */
    @Query("SELECT a FROM AssetEntity a WHERE a.email = :email AND a.stockCode = :stockCode ORDER BY a.transactionDate ASC, "
            + "a.auditMetadata.createdAt ASC NULLS FIRST, a.id ASC")
    List<AssetEntity> findByEmailAndStockCodeOrderByTransactionDate(@Param("email") String email, @Param("stockCode") String stockCode);

    List<AssetEntity> findByEmailAndStockCodeIn(String email, Collection<String> stockCodes);

    /** Oldest first, as {@link #findByEmail}. */
    @Query("SELECT a FROM AssetEntity a WHERE a.email = :email AND a.assetType = :assetType " + OLDEST_FIRST)
    List<AssetEntity> findByEmailAndAssetType(@Param("email") String email, @Param("assetType") AssetType assetType);

    /** Oldest first, as {@link #findByEmail}; both dates included, as the derived query had them. */
    @Query("SELECT a FROM AssetEntity a WHERE a.email = :email AND a.transactionDate BETWEEN :startDate AND :endDate "
            + OLDEST_FIRST)
    List<AssetEntity> findByEmailAndTransactionDateBetween(@Param("email") String email, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Transactional
    void deleteByEmail(String email);
}
