package com.thiru.wealthlens.portfolio.dto.context;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
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
        List<BuyContext> buyContexts,
        TradeSegment segment
) {

    /**
     * Every trade recorded before Chunk 10b was delivery — there was no segment concept anywhere in
     * the application, so nothing else it could have been. This overload lets those call sites stay
     * as they are and say so, rather than sprouting a literal {@code DELIVERY} apiece.
     */
    public ProfitLossContext(String transactionId, double quantity, LocalDate date, double price,
                             String stockCode, BrokerName brokerName, String exchangeName, AssetType assetType,
                             TransactionType transactionType, CorporateActionType actionType,
                             AccountType accountType, String accountHolder, List<BuyContext> buyContexts) {
        this(transactionId, quantity, date, price, stockCode, brokerName, exchangeName, assetType,
                transactionType, actionType, accountType, accountHolder, buyContexts, TradeSegment.DELIVERY);
    }

    /** Never null to a caller: an unset segment reads as delivery. */
    @Override
    public TradeSegment segment() {
        return segment == null ? TradeSegment.DELIVERY : segment;
    }
}
