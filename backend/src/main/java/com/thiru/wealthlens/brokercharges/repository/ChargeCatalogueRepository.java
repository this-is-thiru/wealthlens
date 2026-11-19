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
     * <p>Seeding skips a code already stored, so a catalogue entry that has priced something is
     * never rewritten.
     */
    Optional<ChargeCatalogueEntity> findByCode(String code);

    /** Loaded once by the validator, which checks every rule's code against it. */
    List<ChargeCatalogueEntity> findByStatus(EntityStatus status);
}
