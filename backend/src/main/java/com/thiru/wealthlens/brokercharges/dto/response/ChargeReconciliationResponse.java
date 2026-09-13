package com.thiru.wealthlens.brokercharges.dto.response;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;

public record ChargeReconciliationResponse(
        List<Row> rows,
        double totalComputed,
        double totalEntered,
        double totalDelta,
        int comparableCount,
        int unresolvedCount,
        int transactionsWithoutComputation) {

    public record Row(
            String transactionId,
            String stockCode,
            LocalDate transactionDate,
            BrokerName brokerName,
            AssetType assetType,
            ChargeEvent event,
            ChargeResolution resolution,
            String scheduleCode,
            double computed,
            Double entered,
            Double delta,
            boolean comparable,
            String note) {}
}
