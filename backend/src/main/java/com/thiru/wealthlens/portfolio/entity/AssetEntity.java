package com.thiru.wealthlens.portfolio.entity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.OrderTimeQuantity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(value = "assets")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AssetEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    private String id;

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

    @Field("price")
    private double price;

    @Field("quantity")
    private Double quantity;

    @Field(name = "asset_type", targetType = FieldType.STRING)
    private AssetType assetType;


    /** Delivery for everything recorded before Chunk 10b; there was no segment concept until then. */

    @Field(name = "segment", targetType = FieldType.STRING)

    private TradeSegment segment = TradeSegment.DELIVERY;

    @Field("broker_charges")
    private double brokerCharges;

    @Field("misc_charges")
    private double miscCharges;

    /**
     * How much of {@link #brokerCharges} has already been deducted by earlier sells of this lot.
     *
     * <p>Without it a partial sell divided the <em>full</em> charge by the quantity still remaining,
     * so a 3-unit lot carrying ₹10 sold one unit at a time deducted ₹3.33, then ₹5.00, then ₹10.00
     * — ₹18.33 against ₹10.00 actually paid. That inflates the cost base and understates the gain,
     * which is the direction that under-reports tax (B-7).
     *
     * <p>Absent on a lot written before this field existed, which reads as zero. That is right for
     * a lot never sold, and no worse than the old behaviour for one partially sold already.
     */
    @Field("allocated_buy_charges")
    private double allocatedBuyCharges;

    /** The same, for {@link #miscCharges}. */
    @Field("allocated_buy_misc_charges")
    private double allocatedBuyMiscCharges;

    @JsonFormat(pattern = TCollectionUtil.DATE_FORMAT)
    @Field("maturity_date")
    private LocalDate maturityDate;

    @JsonFormat(pattern = TCollectionUtil.DATE_FORMAT)
    @Field("transaction_date")
    private LocalDate transactionDate;

    @Field("order_id")
    private String orderId;

    // TODO: Remove this as we use orderTimeQuantity
    @Field("order_execution_time")
    private Instant orderExecutionTime;

    @Field("order_time_quantities")
    private List<OrderTimeQuantity> orderTimeQuantities;

    @Field("timezone_id")
    private String timezoneId;

    @Field(name = "account_type", targetType = FieldType.STRING)
    private AccountType accountType;

    @Field("account_holder")
    private String accountHolder;

    @Field(value = "corporate_action", targetType = FieldType.STRING)
    private CorporateActionType corporateActionType;

    @JsonIgnore
    @Field(name = "transaction_type", targetType = FieldType.STRING)
    private TransactionType transactionType;

    @Field("comments")
    private String comment;

    @Field("buy_transaction_ids")
    private List<String> buyTransactionIds = new ArrayList<>();

    @Field("sell_transaction_ids")
    private List<String> sellTransactionIds = new ArrayList<>();

    @Field("corporate_actions")
    private List<CorporateActionEntity> corporateActions = new ArrayList<>();

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();

    // this email we can't accept from the request payload, we are formatting this through the code
    @Transient
    public static final String EMAIL = "email";

    @Transient
    public static Set<String> ALLOWED_FIELDS = Set.of(EMAIL, "transaction_date", "transaction_type",
            "account_type", "account_holder", "exchange_name", "stock_code", "broker_name", "asset_type");

}
