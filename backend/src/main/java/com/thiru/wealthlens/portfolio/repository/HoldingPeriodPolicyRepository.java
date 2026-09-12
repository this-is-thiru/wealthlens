package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.entity.HoldingPeriodPolicyEntity;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HoldingPeriodPolicyRepository extends MongoRepository<HoldingPeriodPolicyEntity, String> {

    Optional<HoldingPeriodPolicyEntity> findByPolicyCode(String policyCode);
}
