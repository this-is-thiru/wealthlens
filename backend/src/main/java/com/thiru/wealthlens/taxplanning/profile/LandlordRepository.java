package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LandlordRepository extends MongoRepository<Landlord, String> {

    /** Everything of this kind a profile holds. */
    List<Landlord> findByProfileId(String profileId);
}
