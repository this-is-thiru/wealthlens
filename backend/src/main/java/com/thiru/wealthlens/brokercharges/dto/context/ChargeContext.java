package com.thiru.wealthlens.brokercharges.dto.context;

import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the engine needs to price one chargeable event.
 *
 * @param orderId            brokerage is capped per order while the engine runs per trade, so
 *                           order-scoped deduplication needs a key
 * @param accountHolder      whose demat account the trade settles in; part of every deduplication
 *                           scope, because depository charges are levied per account
 * @param corporateActionType non-null when the trade arose from a corporate action. Bonus shares
 *                           and split allotments are issued free, so rules must opt in to charging
 *                           them
 * @param lotSize            1 for cash-segment instruments
 * @param baseAmounts        a charge is a percentage <em>of something</em>, and in derivatives that
 *                           something is not one number — options are priced on premium, futures on
 *                           notional, an exercised option on intrinsic value
 * @param lots               FIFO lots consumed by a disposal; empty for a purchase
 * @param attributes         the open-ended valve. Instrument and user facts arrive here and are
 *                           readable by any rule's eligibility predicate, which is what lets a
 *                           broker-owned rule depend on a scheme attribute
 */
public record ChargeContext(
        String transactionId,
        String orderId,
        String stockCode,
        String accountHolder,
        BrokerName brokerName,
        AssetType assetType,
        TradeSegment segment,
        String exchange,
        String planCode,
        ChargeEvent event,
        LocalDate transactionDate,
        CorporateActionType corporateActionType,
        double quantity,
        double price,
        int lotSize,
        Map<AmountBasis, Double> baseAmounts,
        List<LotSlice> lots,
        Map<String, Object> attributes) {

    /** The amount a rule's basis names, or zero when the context does not carry it. */
    public double amount(AmountBasis basis) {
        if (baseAmounts == null || basis == null) {
            return 0.0;
        }
        return baseAmounts.getOrDefault(basis, 0.0);
    }

    /** Whether this event arose from a corporate action rather than an ordinary trade. */
    public boolean isCorporateAction() {
        return corporateActionType != null;
    }
}
