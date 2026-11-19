package com.thiru.wealthlens.taxplanning.profile;

/** What a client sends to create or replace a {@link BankAccount}. */
public record BankAccountRequest(String bankName, String ifsc, String maskedNumber, AccountKind kind, Boolean nominatedForRefund) implements ProfileReferenceRequest<BankAccount> {

    @Override
    public BankAccount applyTo(BankAccount record) {
        ProfileFacts.requireName(bankName);
        ProfileFacts.require(kind, "A bank account needs a kind");
        record.setBankName(bankName);
        record.setIfsc(ifsc);
        record.setMaskedNumber(maskedNumber);
        record.setKind(kind);
        record.setNominatedForRefund(Boolean.TRUE.equals(nominatedForRefund));
        return record;
    }
}
