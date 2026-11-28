package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.InstrumentAliasKind;
import com.thiru.wealthlens.portfolio.entity.InstrumentAliasEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentAliasRepository extends JpaRepository<InstrumentAliasEntity, String> {

    /** Written out because a derived {@code BrokerName = null} never matches: an ISIN's row has no broker. */
    @Query("SELECT a FROM InstrumentAliasEntity a WHERE a.kind = :kind AND a.value = :value"
            + " AND ((:brokerName IS NULL AND a.brokerName IS NULL) OR a.brokerName = :brokerName)")
    Optional<InstrumentAliasEntity> findAlias(@Param("kind") InstrumentAliasKind kind, @Param("brokerName") BrokerName brokerName,
            @Param("value") String value);
}
