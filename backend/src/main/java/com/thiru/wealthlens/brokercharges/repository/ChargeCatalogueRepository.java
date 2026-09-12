package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChargeCatalogueRepository extends MongoRepository<ChargeCatalogueEntity, String> {


    /** Loaded once by the validator, which checks every rule's code against it. */
    List<ChargeCatalogueEntity> findByStatus(EntityStatus status);
}
