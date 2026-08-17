package com.thiru.wealthlens.taxplanning.policy.repository;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PerquisitePolicyRepository extends MongoRepository<PerquisitePolicyEntity, String> {

    Optional<PerquisitePolicyEntity> findByTaxYearAndStatus(String taxYear, EntityStatus status);
}
