package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.taxengine.input.FamilySnapshot;

/** What a client sends to create or replace a {@link Landlord}. */
public record LandlordRequest(String name, String address, String pan, FamilySnapshot.Relationship relationshipToTaxpayer) implements ProfileReferenceRequest<Landlord> {

    @Override
    public Landlord applyTo(Landlord record) {
        ProfileFacts.requireName(name);
        ProfileFacts.requirePanIfGiven(pan);
        record.setName(name);
        record.setAddress(address);
        record.setPan(pan);
        record.setRelationshipToTaxpayer(relationshipToTaxpayer);
        return record;
    }
}
