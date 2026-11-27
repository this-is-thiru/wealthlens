package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandlordRepository extends JpaRepository<Landlord, String> {

    /** Everything of this kind a profile holds. */
    List<Landlord> findByProfileId(String profileId);

    void deleteByProfileId(String profileId);
}
