package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeCatalogueRepository extends JpaRepository<ChargeCatalogueEntity, String> {

    Optional<ChargeCatalogueEntity> findByCode(String code);

    List<ChargeCatalogueEntity> findByStatus(EntityStatus status);
}
