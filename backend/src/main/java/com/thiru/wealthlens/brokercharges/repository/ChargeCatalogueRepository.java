package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChargeCatalogueRepository extends MongoRepository<ChargeCatalogueEntity, String> {

    Optional<ChargeCatalogueEntity> findByCode(String code);

    boolean existsByCode(String code);

    /** Loaded once by the validator, which checks every rule's code against it. */
    List<ChargeCatalogueEntity> findByStatus(EntityStatus status);
}
