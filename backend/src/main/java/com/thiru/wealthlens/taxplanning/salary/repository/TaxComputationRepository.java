package com.thiru.wealthlens.taxplanning.salary.repository;

import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaxComputationRepository extends MongoRepository<TaxComputationEntity, String> {

    List<TaxComputationEntity> findBySalaryProfileId(String salaryProfileId);
}
