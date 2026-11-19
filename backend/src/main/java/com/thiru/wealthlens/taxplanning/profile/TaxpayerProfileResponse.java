package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
import java.time.LocalDate;

/** A profile as a client reads it: the PAN masked ({@code DR 14} O1.1), the storage shape not exposed (TS T10). */
public record TaxpayerProfileResponse(String id, String name, String maskedPan, AssesseeKind kind, LocalDate dateOfBirth,
        ResidentialStatus residentialStatus, DisabilitySeverity disability, LocalDate dateOfDeath, boolean holdsPortfolio) {

    public static TaxpayerProfileResponse of(TaxpayerProfile stored) {
        return new TaxpayerProfileResponse(stored.getId(), stored.getName(), Pan.maskedOrNull(stored.getPan()), stored.getKind(), stored.getDateOfBirth(),
                stored.getResidentialStatus(), stored.getDisability(), stored.getDateOfDeath(), stored.isHoldsPortfolio());
    }
}
