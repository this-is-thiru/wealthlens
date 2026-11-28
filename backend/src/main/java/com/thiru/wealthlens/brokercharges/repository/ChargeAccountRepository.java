package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChargeAccountRepository extends JpaRepository<ChargeAccountEntity, String> {

    Optional<ChargeAccountEntity> findByEmailAndBrokerNameAndDematAccountId(
            String email, BrokerName brokerName, String dematAccountId);

    List<ChargeAccountEntity> findByEmail(String email);

    @Query("SELECT a FROM ChargeAccountEntity a WHERE a.amcFrequency = :frequency"
            + " AND a.status = com.thiru.wealthlens.shared.dto.enums.EntityStatus.ACTIVE"
            + " AND (a.lastBilledThrough IS NULL OR a.lastBilledThrough < :billedThroughBefore)")
    List<ChargeAccountEntity> findDueForAmc(
            @Param("frequency") AmcChargeFrequency frequency,
            @Param("billedThroughBefore") LocalDate billedThroughBefore);

    @Transactional
    void deleteByEmail(String email);
}
