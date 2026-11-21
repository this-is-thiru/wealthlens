package com.thiru.wealthlens.brokercharges.dto.response;

import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.time.LocalDate;
import java.util.List;

/** A rate card as a caller reads it (W-358c2, D-857): its fields, not who stored it — every signed-in user reads these. */
public record ChargeScheduleResponse(String scheduleCode, BrokerName brokerName, AssetType assetType, TradeSegment segment, String exchange, String planCode, LocalDate startDate, LocalDate endDate, EntityStatus status, boolean requiresInstrumentProfile, List<ChargeRule> rules, String sourceUrl, LocalDate verifiedOn) {

    public static ChargeScheduleResponse from(ChargeScheduleEntity entity) {
        return new ChargeScheduleResponse(entity.getScheduleCode(), entity.getBrokerName(), entity.getAssetType(), entity.getSegment(), entity.getExchange(), entity.getPlanCode(), entity.getStartDate(), entity.getEndDate(), entity.getStatus(), entity.isRequiresInstrumentProfile(), entity.getRules(), entity.getSourceUrl(), entity.getVerifiedOn());
    }
}
