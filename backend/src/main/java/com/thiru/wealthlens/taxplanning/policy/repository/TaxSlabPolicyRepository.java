package com.thiru.wealthlens.taxplanning.policy.repository;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaxSlabPolicyRepository extends MongoRepository<TaxSlabPolicyEntity, String> {

    Optional<TaxSlabPolicyEntity> findByTaxYearAndRegimeTypeAndStatus(String taxYear, RegimeType regimeType, EntityStatus status);

    List<TaxSlabPolicyEntity> findByTaxYearAndRegimeType(String taxYear, RegimeType regimeType);

    List<TaxSlabPolicyEntity> findByTaxYear(String taxYear);
}
