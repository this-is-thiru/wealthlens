package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChargeScheduleRepository extends JpaRepository<ChargeScheduleEntity, String> {

    @Query("SELECT s FROM ChargeScheduleEntity s WHERE s.brokerName = :brokerName"
            + " AND (s.status IS NULL OR s.status <> com.thiru.wealthlens.shared.dto.enums.EntityStatus.INACTIVE)"
            + " AND s.startDate <= :transactionDate"
            + " AND (s.endDate IS NULL OR s.endDate >= :transactionDate)")
    List<ChargeScheduleEntity> findCandidates(
            @Param("brokerName") BrokerName brokerName, @Param("transactionDate") LocalDate transactionDate);

    @Query("SELECT s FROM ChargeScheduleEntity s WHERE s.brokerName IS NULL"
            + " AND (s.status IS NULL OR s.status <> com.thiru.wealthlens.shared.dto.enums.EntityStatus.INACTIVE)"
            + " AND s.startDate <= :transactionDate"
            + " AND (s.endDate IS NULL OR s.endDate >= :transactionDate)")
    List<ChargeScheduleEntity> findStatutoryCandidates(@Param("transactionDate") LocalDate transactionDate);

    Optional<ChargeScheduleEntity> findByScheduleCode(String scheduleCode);

    @Query("SELECT s FROM ChargeScheduleEntity s"
            + " WHERE ((:brokerName IS NULL AND s.brokerName IS NULL) OR s.brokerName = :brokerName)"
            + " AND ((:assetType IS NULL AND s.assetType IS NULL) OR CAST(s.assetType AS string) = :assetType)"
            + " AND ((:segment IS NULL AND s.segment IS NULL) OR CAST(s.segment AS string) = :segment)"
            + " AND ((:exchange IS NULL AND s.exchange IS NULL) OR s.exchange = :exchange)"
            + " AND ((:planCode IS NULL AND s.planCode IS NULL) OR s.planCode = :planCode)"
            + " AND (s.status IS NULL OR s.status <> com.thiru.wealthlens.shared.dto.enums.EntityStatus.INACTIVE)"
            + " AND s.endDate IS NULL")
    Optional<ChargeScheduleEntity> findOpenScheduleForScope(
            @Param("brokerName") BrokerName brokerName, @Param("assetType") String assetType,
            @Param("segment") String segment, @Param("exchange") String exchange,
            @Param("planCode") String planCode);

    List<ChargeScheduleEntity> findByBrokerNameOrderByStartDateDesc(BrokerName brokerName);

    List<ChargeScheduleEntity> findByVerifiedOnIsNullOrVerifiedOnBefore(LocalDate horizon);
}
