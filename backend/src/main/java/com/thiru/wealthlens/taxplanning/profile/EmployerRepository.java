package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerRepository extends JpaRepository<Employer, String> {

    /** Everything of this kind a profile holds. */
    List<Employer> findByProfileId(String profileId);

    void deleteByProfileId(String profileId);
}
