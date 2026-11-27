package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LenderRepository extends JpaRepository<Lender, String> {

    /** Everything of this kind a profile holds. */
    List<Lender> findByProfileId(String profileId);

    void deleteByProfileId(String profileId);
}
