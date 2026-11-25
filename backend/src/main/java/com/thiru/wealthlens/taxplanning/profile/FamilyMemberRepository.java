package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, String> {

    /** Everything of this kind a profile holds. */
    List<FamilyMember> findByProfileId(String profileId);

    void deleteByProfileId(String profileId);
}
