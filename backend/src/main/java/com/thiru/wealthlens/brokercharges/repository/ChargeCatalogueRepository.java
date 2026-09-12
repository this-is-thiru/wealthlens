package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChargeCatalogueRepository extends MongoRepository<ChargeCatalogueEntity, String> {

    /**
     * The stored row for a code, or empty.
     *
     * <p>Used both to decide whether seeding should skip a code and to backfill
     * {@code deductibleForCapitalGains} onto one stored before that field existed, so it has to
     * return the entity rather than a boolean.
     */
    Optional<ChargeCatalogueEntity> findByCode(String code);

    /** Loaded once by the validator, which checks every rule's code against it. */
    List<ChargeCatalogueEntity> findByStatus(EntityStatus status);
}
