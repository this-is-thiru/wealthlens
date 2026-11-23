package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChargeInstrumentRepository extends JpaRepository<ChargeInstrumentEntity, String> {

    @Query("SELECT i FROM ChargeInstrumentEntity i WHERE i.stockCode = :stockCode"
            + " AND (i.status IS NULL OR i.status <> com.thiru.wealthlens.shared.dto.enums.EntityStatus.INACTIVE)"
            + " AND i.startDate <= :transactionDate"
            + " AND (i.endDate IS NULL OR i.endDate >= :transactionDate)")
    List<ChargeInstrumentEntity> findCandidates(
            @Param("stockCode") String stockCode, @Param("transactionDate") LocalDate transactionDate);

    Optional<ChargeInstrumentEntity> findByStockCodeAndStartDate(String stockCode, LocalDate startDate);
}
