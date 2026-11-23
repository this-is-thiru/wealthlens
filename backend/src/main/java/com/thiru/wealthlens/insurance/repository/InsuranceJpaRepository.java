package com.thiru.wealthlens.insurance.repository;

import com.thiru.wealthlens.insurance.entity.InsuranceJpa;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code InsuranceEntity}'s PostgreSQL counterpart, proving the mapping (W-337a, D-937); no Mongo repository exists to name, since nothing writes policies today. */
public interface InsuranceJpaRepository extends JpaRepository<InsuranceJpa, String> {

    List<InsuranceJpa> findByEmail(String email);

    void deleteByEmail(String email);
}
