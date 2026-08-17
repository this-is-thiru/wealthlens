package com.thiru.wealthlens.taxplanning.salary.repository;

import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SalaryProfileRepository extends MongoRepository<SalaryProfileEntity, String> {

    List<SalaryProfileEntity> findByEmailOrderByAuditMetadata_CreatedAtDesc(String email);
}
