package com.thiru.wealthlens.brokercharges.dto.request;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.time.LocalDate;

/**
 * What a user sends to register a demat account (W-206). It names only what the user knows about the account: the email comes from
 * the token, and the billing state ({@code lastBilledThrough}, {@code billingEvents}) is the AMC run's, so a body carrying either is
 * refused as an unknown field rather than stored — a user who set it would skip every cycle up to it.
 */
public record ChargeAccountRequest(String accountHolder, BrokerName brokerName, String dematAccountId, String planCode, LocalDate openedOn,
        AmcChargeFrequency amcFrequency, EntityStatus status) {

    /** The account to register for {@code email}, the caller. */
    public ChargeAccountEntity asAccount(String email) {
        ChargeAccountEntity account = new ChargeAccountEntity();
        account.setEmail(email);
        account.setAccountHolder(accountHolder);
        account.setBrokerName(brokerName);
        account.setDematAccountId(dematAccountId);
        account.setPlanCode(planCode);
        account.setOpenedOn(openedOn);
        account.setAmcFrequency(amcFrequency);
        account.setStatus(status);
        return account;
    }
}
