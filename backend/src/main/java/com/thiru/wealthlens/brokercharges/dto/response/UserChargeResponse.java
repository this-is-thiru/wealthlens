package com.thiru.wealthlens.brokercharges.dto.response;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** What one trade was charged, as its user or the owner reads it (W-358c2, D-857). */
public record UserChargeResponse(String email, String accountHolder, BrokerName brokerName, AssetType assetType, TradeSegment segment, String exchange, String stockCode, String transactionId, String orderId, ChargeEvent event, LocalDate transactionDate, LocalDateTime computedOn, ChargeResolution resolution, String scheduleId, String scheduleCode, String statutoryScheduleId, String statutoryScheduleCode, String instrumentId, double turnover, double quantity, List<ChargeLine> lines, Map<String, Double> amountByCode, double totalCharges) {

    public static UserChargeResponse from(UserChargeEntity entity) {
        return new UserChargeResponse(entity.getEmail(), entity.getAccountHolder(), entity.getBrokerName(), entity.getAssetType(), entity.getSegment(), entity.getExchange(), entity.getStockCode(), entity.getTransactionId(), entity.getOrderId(), entity.getEvent(), entity.getTransactionDate(), entity.getComputedOn(), entity.getResolution(), entity.getScheduleId(), entity.getScheduleCode(), entity.getStatutoryScheduleId(), entity.getStatutoryScheduleCode(), entity.getInstrumentId(), entity.getTurnover(), entity.getQuantity(), entity.getLines(), entity.getAmountByCode(), entity.getTotalCharges());
    }
}
