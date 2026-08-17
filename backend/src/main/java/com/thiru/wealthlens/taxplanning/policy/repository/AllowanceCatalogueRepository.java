package com.thiru.wealthlens.taxplanning.policy.repository;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AllowanceCatalogueRepository extends MongoRepository<AllowanceCatalogueEntity, String> {

    List<AllowanceCatalogueEntity> findByTaxYearAndStatus(String taxYear, EntityStatus status);

    List<AllowanceCatalogueEntity> findByTaxYearAndRegimeTypeAndStatus(String taxYear, RegimeType regimeType, EntityStatus status);

    Optional<AllowanceCatalogueEntity> findByCodeAndTaxYear(String code, String taxYear);

    Optional<AllowanceCatalogueEntity> findByCodeAndTaxYearAndRegimeType(String code, String taxYear, RegimeType regimeType);

    Optional<AllowanceCatalogueEntity> findByCodeAndTaxYearAndRegimeTypeAndStatus(String code, String taxYear, RegimeType regimeType, EntityStatus status);

    Optional<AllowanceCatalogueEntity> findByCodeAndStatus(String code, EntityStatus status);
}
