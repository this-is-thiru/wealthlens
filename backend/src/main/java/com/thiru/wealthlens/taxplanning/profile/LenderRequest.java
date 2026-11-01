package com.thiru.wealthlens.taxplanning.profile;

/** What a client sends to create or replace a {@link Lender}. */
public record LenderRequest(String name, String pan, LenderKind kind) implements ProfileReferenceRequest<Lender> {

    @Override
    public Lender applyTo(Lender record) {
        ProfileFacts.requireName(name);
        ProfileFacts.requirePanIfGiven(pan);
        ProfileFacts.require(kind, "A lender needs a kind");
        record.setName(name);
        record.setPan(pan);
        record.setKind(kind);
        return record;
    }
}
