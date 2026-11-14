package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.taxengine.input.FamilySnapshot;

/** A landlord as a client reads it, the PAN masked. */
public record LandlordResponse(String id, String name, String address, String maskedPan, FamilySnapshot.Relationship relationshipToTaxpayer) {

    public static LandlordResponse of(Landlord stored) {
        return new LandlordResponse(stored.getId(), stored.getName(), stored.getAddress(), Pan.maskedOrNull(stored.getPan()), stored.getRelationshipToTaxpayer());
    }
}
