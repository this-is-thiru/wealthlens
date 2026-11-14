package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import java.time.LocalDate;

/** A family member as a client reads it. */
public record FamilyMemberResponse(String id, String name, FamilySnapshot.Relationship relationship, LocalDate dateOfBirth, boolean dependant,
        DisabilitySeverity disability, LocalDate dateOfDeath, boolean multipleBirthAfterFirstChild, boolean residentInIndia,
        String maskedPan) {

    public static FamilyMemberResponse of(FamilyMember stored) {
        return new FamilyMemberResponse(stored.getId(), stored.getName(), stored.getRelationship(), stored.getDateOfBirth(),
                stored.isDependant(), stored.getDisability(), stored.getDateOfDeath(), stored.isMultipleBirthAfterFirstChild(),
                stored.isResidentInIndia(), Pan.maskedOrNull(stored.getPan()));
    }
}
