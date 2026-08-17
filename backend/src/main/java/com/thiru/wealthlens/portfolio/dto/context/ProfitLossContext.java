package com.thiru.wealthlens.portfolio.dto.context;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import java.time.LocalDate;
import java.util.List;

public record ProfitLossContext(
        String transactionId,
        double quantity,
        LocalDate date,
        double price,
        String stockCode,
        BrokerName brokerName,
        String exchangeName,
        AssetType assetType,
        TransactionType transactionType,
        CorporateActionType actionType,
        AccountType accountType,
        String accountHolder,
        List<BuyContext> buyContexts
) {
}
