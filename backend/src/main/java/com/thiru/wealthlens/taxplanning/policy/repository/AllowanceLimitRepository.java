package com.thiru.wealthlens.taxplanning.policy.repository;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AllowanceLimitRepository extends MongoRepository<AllowanceLimitEntity, String> {

    Optional<AllowanceLimitEntity> findTopByAllowanceCodeAndTaxYearAndRegimeTypeAndEmployerTypeAndStatusOrderByEffectiveDateDesc(
            String allowanceCode, String taxYear, RegimeType regimeType, EmployerType employerType, EntityStatus status);

    Optional<AllowanceLimitEntity> findTopByAllowanceCodeAndTaxYearAndRegimeTypeAndStatusOrderByEffectiveDateDesc(
            String allowanceCode, String taxYear, RegimeType regimeType, EntityStatus status);

    Optional<AllowanceLimitEntity> findTopByAllowanceCodeAndTaxYearAndStatusOrderByEffectiveDateDesc(
            String allowanceCode, String taxYear, EntityStatus status);

    List<AllowanceLimitEntity> findByTaxYearAndStatus(String taxYear, EntityStatus status);
}
