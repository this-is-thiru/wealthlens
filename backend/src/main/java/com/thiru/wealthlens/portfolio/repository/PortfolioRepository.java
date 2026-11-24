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

    List<AssetEntity> findByEmail(String email);

    /**
     * The oldest lot of a stock bought on a day in a segment — the one V1's buy merges into; V2 may have bought several
     * (W-272). A buy of another segment that day is a lot of its own (W-296).
     *
     * <p>Ordered by creation time, not id (W-337e, D-949): Mongo's {@code ObjectId} is itself time-ordered, so
     * {@code OrderByIdAsc} doubled as "oldest first" there; the id here is a random UUID (W-337a's convention), which
     * carries no temporal order at all.
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

    List<AssetEntity> findByEmailAndStockCodeOrderByTransactionDate(String email, String stockCode);

    List<AssetEntity> findByEmailAndStockCodeIn(String email, Collection<String> stockCodes);

    List<AssetEntity> findByEmailAndAssetType(String email, AssetType assetType);

    List<AssetEntity> findByEmailAndTransactionDateBetween(String email, LocalDate startDate, LocalDate endDate);

    @Transactional("postgresTransactionManager")
    void deleteByEmail(String email);
}
