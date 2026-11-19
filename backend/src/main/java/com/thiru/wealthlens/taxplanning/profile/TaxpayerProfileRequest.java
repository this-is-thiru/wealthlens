package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
import java.time.LocalDate;

/** What a client sends to create or replace a {@link TaxpayerProfile}. */
public record TaxpayerProfileRequest(String name, String pan, AssesseeKind kind, LocalDate dateOfBirth,
        ResidentialStatus residentialStatus, DisabilitySeverity disability, LocalDate dateOfDeath, Boolean holdsPortfolio) {

    TaxpayerProfile applyTo(TaxpayerProfile profile) {
        ProfileFacts.requireName(name);
        new Pan(pan);
        ProfileFacts.require(kind, "A profile needs a kind");
        ProfileFacts.require(residentialStatus, "A profile needs a residential status");
        if (kind == AssesseeKind.INDIVIDUAL) {
            ProfileFacts.require(dateOfBirth, "An individual needs a date of birth");
        }
        profile.setName(name);
        profile.setPan(pan);
        profile.setKind(kind);
        profile.setDateOfBirth(dateOfBirth);
        profile.setResidentialStatus(residentialStatus);
        profile.setDisability(disability);
        profile.setDateOfDeath(dateOfDeath);
        profile.setHoldsPortfolio(Boolean.TRUE.equals(holdsPortfolio));
        return profile;
    }
}
