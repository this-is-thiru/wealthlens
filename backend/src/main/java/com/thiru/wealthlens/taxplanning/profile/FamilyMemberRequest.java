package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import java.time.LocalDate;

/** What a client sends to create or replace a {@link FamilyMember}. */
public record FamilyMemberRequest(String name, FamilySnapshot.Relationship relationship, LocalDate dateOfBirth, Boolean dependant,
        DisabilityLevel disability, LocalDate dateOfDeath, Boolean multipleBirthAfterFirstChild, Boolean residentInIndia,
        String pan) implements ProfileReferenceRequest<FamilyMember> {

    @Override
    public FamilyMember applyTo(FamilyMember record) {
        ProfileFacts.requireName(name);
        ProfileFacts.requirePanIfGiven(pan);
        ProfileFacts.require(relationship, "A family member needs a relationship");
        ProfileFacts.require(dateOfBirth, "A family member needs a date of birth");
        record.setName(name);
        record.setRelationship(relationship);
        record.setDateOfBirth(dateOfBirth);
        record.setDependant(Boolean.TRUE.equals(dependant));
        record.setDisability(disability);
        record.setDateOfDeath(dateOfDeath);
        record.setMultipleBirthAfterFirstChild(Boolean.TRUE.equals(multipleBirthAfterFirstChild));
        record.setResidentInIndia(Boolean.TRUE.equals(residentInIndia));
        record.setPan(pan);
        return record;
    }
}
