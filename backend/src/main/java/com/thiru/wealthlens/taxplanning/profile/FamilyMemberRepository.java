package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FamilyMemberRepository extends MongoRepository<FamilyMember, String> {

    /** Everything of this kind a profile holds. */
    List<FamilyMember> findByProfileId(String profileId);
}
