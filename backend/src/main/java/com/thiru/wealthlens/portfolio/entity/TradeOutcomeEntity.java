package com.thiru.wealthlens.portfolio.entity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(value = "trade_outcomes")
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

    @Field("ca_adjusted_buy_price")
    private double caAdjustedBuyPrice;

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

    @Field("financial_year")
    private String financialYear;

    // Linkage fields for traceability
    @Field("source_sell_transaction_id")
    private String sourceSellTransactionId;

    @Field("source_buy_lot_id")
    private String sourceBuyLotId;

    // CA tracking
    @Field("is_ca_derived")
    private Boolean isCaDerived;

    @Field("applied_corporate_actions")
    private List<CorporateActionEntity> appliedCorporateActions = new ArrayList<>();

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
