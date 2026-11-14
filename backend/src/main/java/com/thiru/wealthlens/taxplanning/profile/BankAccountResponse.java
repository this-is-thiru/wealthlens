package com.thiru.wealthlens.taxplanning.profile;

/** A bank account as a client reads it. */
public record BankAccountResponse(String id, String bankName, String ifsc, String maskedNumber, AccountKind kind, boolean nominatedForRefund) {

    public static BankAccountResponse of(BankAccount stored) {
        return new BankAccountResponse(stored.getId(), stored.getBankName(), stored.getIfsc(), stored.getMaskedNumber(), stored.getKind(),
                stored.isNominatedForRefund());
    }
}
