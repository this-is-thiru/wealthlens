package com.thiru.wealthlens.brokercharges.dto.request;

import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What an admin sends to publish a rate card (W-206): the card's scope, window and rules, never who wrote it, which the server records.
 * The rules are {@link ChargeRule} as stored, the card's own language with nothing in it the server owns (C4a). An omitted
 * {@code requiresInstrumentProfile} means false, as it did when the card itself was the body (C3a).
 */
public record ChargeScheduleRequest(String scheduleCode, BrokerName brokerName, AssetType assetType, TradeSegment segment, String exchange,
        String planCode, LocalDate startDate, LocalDate endDate, EntityStatus status, Boolean requiresInstrumentProfile, List<ChargeRule> rules,
        String sourceUrl, LocalDate verifiedOn) {

    /** The card to publish. */
    public ChargeScheduleEntity asSchedule() {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode(scheduleCode);
        schedule.setBrokerName(brokerName);
        schedule.setAssetType(assetType);
        schedule.setSegment(segment);
        schedule.setExchange(exchange);
        schedule.setPlanCode(planCode);
        schedule.setStartDate(startDate);
        schedule.setEndDate(endDate);
        schedule.setStatus(status);
        schedule.setRequiresInstrumentProfile(Boolean.TRUE.equals(requiresInstrumentProfile));
        schedule.setRules(rules == null ? new ArrayList<>() : new ArrayList<>(rules));
        schedule.setSourceUrl(sourceUrl);
        schedule.setVerifiedOn(verifiedOn);
        return schedule;
    }
}
