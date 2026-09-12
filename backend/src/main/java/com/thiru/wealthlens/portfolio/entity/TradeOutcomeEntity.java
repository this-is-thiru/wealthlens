package com.thiru.wealthlens.portfolio.entity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(value = "trade_outcomes")
/**
 * Indexed on {@code {email, sell_date}} because every read is one user's realised trades, and a
 * capital-gains statement wants them in date order. Created by {@code PortfolioIndexInitializer} —
 * {@code auto-index-creation} is off application-wide, so this annotation alone creates nothing.
 */
@CompoundIndex(name = "trade_outcome_user_idx", def = "{'email': 1, 'sell_date': -1}")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TradeOutcomeEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    private String id;

    // Identity fields
    @Field("email")
    private String email;

    @Field("stock_code")
    private String stockCode;

    @Field("stock_name")
    private String stockName;

    @Field("exchange_name")
    private String exchangeName;

    @Field(name = "broker_name", targetType = FieldType.STRING)
    private BrokerName brokerName;

    @Field(name = "asset_type", targetType = FieldType.STRING)
    private AssetType assetType;

    @Field(name = "account_type", targetType = FieldType.STRING)
    private AccountType accountType;

    @Field("account_holder")
    private String accountHolder;

    // Buy side
    @Field("original_buy_price")
    private double originalBuyPrice;

    /**
     * The buy price after corporate-action adjustment — bonus, split, demerger.
     *
     * <p>"CA" here is <b>corporate action</b>, never chartered accountant. The stored field keeps its
     * original abbreviated name because live documents carry it; the Java name spells it out.
     */
    @Field("ca_adjusted_buy_price")
    private double corporateActionAdjustedBuyPrice;

    @Field("buy_quantity")
    private Double buyQuantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Field("buy_date")
    private LocalDate buyDate;

    @Field("buy_broker_charges")
    private double buyBrokerCharges;

    @Field("buy_misc_charges")
    private double buyMiscCharges;

    // Sell side
    @Field("sell_price")
    private double sellPrice;

    @Field("sell_quantity")
    private Double sellQuantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Field("sell_date")
    private LocalDate sellDate;

    @Field("sell_broker_charges")
    private double sellBrokerCharges;

    @Field("sell_misc_charges")
    private double sellMiscCharges;

    // Computed fields
    @Field("total_buy_value")
    private double totalBuyValue;

    @Field("total_sell_value")
    private double totalSellValue;

    @Field("net_profit")
    private double netProfit;

    @Field("profit_percentage")
    private double profitPercentage;

    @Field("holding_period_days")
    private Long holdingPeriodDays;

    @Field(name = "capital_gains_type", targetType = FieldType.STRING)
    private CapitalGainsType capitalGainsType;


    /**
     * How the disposal was traded. Decides speculative and derivative treatment before holding
     * period is consulted at all (D1), so a row without it cannot be classified after the fact.
     */
    @Field(name = "segment", targetType = FieldType.STRING)
    private TradeSegment segment;

    /**
     * The second dimension behind {@link #capitalGainsType} — {@code EQUITY_ORIENTED} /
     * {@code SPECIFIED} / {@code OTHER} for a scheme, {@code LISTED} / {@code UNLISTED} for a bond.
     *
     * <p>Null until the instrument registry ships (D7), which is why mutual funds and bonds record
     * {@code UNCLASSIFIED}. Stored explicitly rather than inferred so a reader can tell a row that
     * was classified as unknown from one written before the field existed.
     */
    @Field("instrument_sub_class")
    private String instrumentSubClass;

    /** Why the classification came out as it did — the only place an UNCLASSIFIED row says which kind of unknown it is. */
    @Field("classification_reason")
    private String classificationReason;

    /**
     * What the buy actually cost, by charge code, rather than one lumped total.
     *
     * <p>Deductible cost is a sum over codes because only some charges reduce a capital gain (D3),
     * and which ones is a property of the code. A single total cannot be split after the fact.
     */
    @Field("buy_charge_breakup")
    private Map<String, Double> buyChargeBreakup = new LinkedHashMap<>();

    /** The same for the sell side, pro-rated to this lot's share of the disposal. */
    @Field("sell_charge_breakup")
    private Map<String, Double> sellChargeBreakup = new LinkedHashMap<>();

    /** The part of {@link #buyChargeBreakup} that reduces the gain, resolved against the catalogue. */
    @Field("deductible_buy_charges")
    private double deductibleBuyCharges;

    /** The part of {@link #sellChargeBreakup} that reduces the gain. Notably excludes STT. */
    @Field("deductible_sell_charges")
    private double deductibleSellCharges;

    @Field("financial_year")
    private String financialYear;

    // Linkage fields for traceability
    @Field("source_sell_transaction_id")
    private String sourceSellTransactionId;

    @Field("source_buy_lot_id")
    private String sourceBuyLotId;

    // CA tracking
    /**
     * Whether this holding arose from a corporate action rather than a purchase — a bonus allotment
     * or a split, which are issued free and so have no acquisition cost of their own.
     *
     * <p>"CA" here is <b>corporate action</b>. The stored field keeps its abbreviated name because
     * live documents carry it; the Java name spells it out.
     */
    @Field("is_ca_derived")
    private Boolean corporateActionDerived;

    @Field("applied_corporate_actions")
    private List<CorporateActionEntity> appliedCorporateActions = new ArrayList<>();

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
