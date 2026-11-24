package com.thiru.wealthlens.corporate.repository;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorporateActionRepository extends JpaRepository<CorporateActionEntity, String> {

    List<CorporateActionEntity> findByType(CorporateActionType type);

    /** Every action of these types on a stock, whatever its date: the {@code assets} rebuild sorts them (W-283a2). */
    List<CorporateActionEntity> findByStockCodeAndTypeIn(String stockCode, List<CorporateActionType> types);

    /** Corporate actions to perform before the transaction date. */
    List<CorporateActionEntity> findByStockCodeAndTypeInAndRecordDateBefore(
            String stockCode, List<CorporateActionType> types, LocalDate transactionDate);

    @Query("SELECT c FROM CorporateActionEntity c WHERE c.stockCode = :stockCode AND c.type IN :types"
            + " AND c.recordDate BETWEEN :start AND :end")
    List<CorporateActionEntity> findByStockCodeAndTypeInAndRecordDateBetween(
            @Param("stockCode") String stockCode, @Param("types") List<CorporateActionType> types,
            @Param("start") LocalDate start, @Param("end") LocalDate transactionDate);

    /** Oldest record date first, then priority: a later action applies to what an earlier one left (W-314). */
    @Query("SELECT c FROM CorporateActionEntity c WHERE c.type IN :types AND c.recordDate BETWEEN :start AND :end"
            + " ORDER BY c.recordDate ASC, c.priority ASC")
    List<CorporateActionEntity> findByTypeInAndRecordDateBetween(
            @Param("types") List<CorporateActionType> types,
            @Param("start") LocalDate start, @Param("end") LocalDate transactionDate);

    @Query("SELECT c FROM CorporateActionEntity c WHERE c.stockCode = :stockCode AND c.recordDate = :recordDate"
            + " ORDER BY c.priority ASC")
    List<CorporateActionEntity> findByStockCodeAndRecordDateAndOrderByPriorityAsc(
            @Param("stockCode") String stockCode, @Param("recordDate") LocalDate recordDate);

    Page<CorporateActionEntity> findByIdIn(Collection<String> ids, Pageable pageable);
}
