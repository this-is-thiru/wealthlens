package com.thiru.wealthlens.insurance.repository;

import com.thiru.wealthlens.insurance.entity.InsuranceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsuranceRepository extends JpaRepository<InsuranceEntity, String> {

    List<InsuranceEntity> findByEmail(String email);

    void deleteByEmail(String email);
}
