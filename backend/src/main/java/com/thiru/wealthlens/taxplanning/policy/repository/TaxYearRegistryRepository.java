package com.thiru.wealthlens.taxplanning.policy.repository;

import com.thiru.wealthlens.taxplanning.policy.entity.TaxYearRegistryEntity;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaxYearRegistryRepository extends MongoRepository<TaxYearRegistryEntity, String> {

    Optional<TaxYearRegistryEntity> findByTaxYear(String taxYear);
}
