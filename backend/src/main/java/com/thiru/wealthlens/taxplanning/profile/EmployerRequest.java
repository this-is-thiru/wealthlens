package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.taxplanning.enums.EmployerType;

/** What a client sends to create or replace a {@link Employer}. */
public record EmployerRequest(String name, String tan, String pan, EmployerType type, String address) implements ProfileReferenceRequest<Employer> {

    @Override
    public Employer applyTo(Employer record) {
        ProfileFacts.requireName(name);
        ProfileFacts.requirePanIfGiven(pan);
        new Tan(tan);
        ProfileFacts.require(type, "An employer needs a type");
        record.setName(name);
        record.setTan(tan);
        record.setPan(pan);
        record.setType(type);
        record.setAddress(address);
        return record;
    }
}
