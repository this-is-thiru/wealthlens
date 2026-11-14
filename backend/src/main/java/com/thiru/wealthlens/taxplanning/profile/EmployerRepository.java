package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmployerRepository extends MongoRepository<Employer, String> {

    /** Everything of this kind a profile holds. */
    List<Employer> findByProfileId(String profileId);
}
