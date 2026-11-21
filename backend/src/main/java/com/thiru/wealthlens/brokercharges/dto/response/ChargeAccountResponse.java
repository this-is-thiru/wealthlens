package com.thiru.wealthlens.brokercharges.dto.response;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.time.LocalDate;
import java.util.List;

/** A demat account registered for account-level charges, as its user or the owner reads it (W-358c2, D-857). */
public record ChargeAccountResponse(String email, String accountHolder, BrokerName brokerName, String dematAccountId, String planCode, LocalDate openedOn, AmcChargeFrequency amcFrequency, LocalDate lastBilledThrough, List<ChargeAccountEntity.BillingEvent> billingEvents, EntityStatus status) {

    public static ChargeAccountResponse from(ChargeAccountEntity entity) {
        return new ChargeAccountResponse(entity.getEmail(), entity.getAccountHolder(), entity.getBrokerName(), entity.getDematAccountId(), entity.getPlanCode(), entity.getOpenedOn(), entity.getAmcFrequency(), entity.getLastBilledThrough(), entity.getBillingEvents(), entity.getStatus());
    }
}
