package com.thiru.wealthlens.brokercharges.dto.context;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerChargeTransactionType;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;

public record BrokerChargeContext(
        String transactionId,
        String stockCode,
        BrokerName brokerName,
        BrokerChargeTransactionType transactionType,
        LocalDate transactionDate,
        String exchangeName,
        CorporateActionType corporateActionType,
        double totalAmount
) {
}
