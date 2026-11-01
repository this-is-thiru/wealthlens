package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LenderRepository extends MongoRepository<Lender, String> {

    /** Everything of this kind a profile holds. */
    List<Lender> findByProfileId(String profileId);
}
