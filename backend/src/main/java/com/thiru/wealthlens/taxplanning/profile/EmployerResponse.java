package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.taxplanning.enums.EmployerType;

/** An employer as a client reads it, its identifiers masked. */
public record EmployerResponse(String id, String name, String maskedTan, String maskedPan, EmployerType type, String address) {

    public static EmployerResponse of(Employer stored) {
        return new EmployerResponse(stored.getId(), stored.getName(), new Tan(stored.getTan()).masked(), Pan.maskedOrNull(stored.getPan()), stored.getType(),
                stored.getAddress());
    }
}
