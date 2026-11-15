package com.thiru.wealthlens.taxplanning.profile;

/** A lender as a client reads it, the PAN masked. */
public record LenderResponse(String id, String name, String address, String maskedPan, LenderKind kind) {

    public static LenderResponse of(Lender stored) {
        return new LenderResponse(stored.getId(), stored.getName(), stored.getAddress(), Pan.maskedOrNull(stored.getPan()),
                stored.getKind());
    }
}
