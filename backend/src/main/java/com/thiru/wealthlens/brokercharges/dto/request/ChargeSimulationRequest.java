package com.thiru.wealthlens.brokercharges.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A trade to price without recording it.
 *
 * <p>Most fields mirror {@code ChargeContext}. Two are worth stating plainly:
 *
 * <p>{@code email} and {@code accountHolder} are optional, and what they change is deduplication.
 * Supplied, a scoped charge such as the depository fee is checked against the charges already
 * recorded for that account that day, so the answer is what a <em>second</em> sale would really
 * cost. Omitted, every scoped charge is priced as a first occurrence — the honest default for an
 * anonymous preview, and the more expensive of the two, which is the safer way to be wrong.
 *
 * <p>{@code baseAmounts} is optional and, when omitted, derived as price × quantity × lot size.
 * Derivatives are why it can be stated instead: an option is charged on its premium, a future on
 * its notional, and pricing an option on notional is wrong by orders of magnitude rather than by
 * paise.
 */
@Data
@NoArgsConstructor
@ToString
public class ChargeSimulationRequest {

    private String email;

    private String accountHolder;

    private String stockCode;

    private String orderId;

    private BrokerName brokerName;

    private AssetType assetType;

    private TradeSegment segment;

    private String exchange;

    private String planCode;

    private ChargeEvent event;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;

    /** Set when the trade arose from a corporate action, which rules must opt in to charging. */
    private CorporateActionType corporateActionType;

    private double quantity;

    private double price;

    /** 1 for cash-segment instruments; unset is read as 1 rather than as zero. */
    private int lotSize;

    private Map<AmountBasis, Double> baseAmounts;

    /** Instrument and user facts a rule's eligibility predicate can read. */
    private Map<String, Object> attributes;
}
