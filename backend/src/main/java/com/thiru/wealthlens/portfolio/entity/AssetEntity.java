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
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.protection.SearchableColumn;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "assets")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AssetEntity {

    @JsonIgnore
    @Id
    private String id;

    @Column(name = "email", nullable = false)
    @Convert(converter = SearchableColumn.class)
    private String email;

    @Column(name = "stock_code")
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    @Column(name = "exchange_name")
    private String exchangeName;

    @Column(name = "broker_name")
    @Enumerated(EnumType.STRING)
    private BrokerName brokerName;

    @Column(name = "price")
    private double price;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "asset_type")
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    /** Delivery for everything recorded before Chunk 10b; there was no segment concept until then. */
    @Column(name = "segment")
    @Enumerated(EnumType.STRING)
    private TradeSegment segment = TradeSegment.DELIVERY;

    @Column(name = "broker_charges")
    private double brokerCharges;

    @Column(name = "misc_charges")
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
    @Column(name = "allocated_buy_charges")
    private double allocatedBuyCharges;

    /** The same, for {@link #miscCharges}. */
    @Column(name = "allocated_buy_misc_charges")
    private double allocatedBuyMiscCharges;

    @JsonFormat(pattern = TCollectionUtil.DATE_FORMAT)
    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @JsonFormat(pattern = TCollectionUtil.DATE_FORMAT)
    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "order_id")
    private String orderId;

    // TODO(W-214): Remove this as we use orderTimeQuantity
    @Column(name = "order_execution_time")
    private Instant orderExecutionTime;

    @Column(name = "order_time_quantities_json")
    @Convert(converter = OrderTimeQuantityListConverter.class)
    private List<OrderTimeQuantity> orderTimeQuantities;

    @Column(name = "timezone_id")
    private String timezoneId;

    @Column(name = "account_type")
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(name = "account_holder")
    @Convert(converter = SearchableColumn.class)
    private String accountHolder;

    @Column(name = "corporate_action")
    @Enumerated(EnumType.STRING)
    private CorporateActionType corporateActionType;

    @JsonIgnore
    @Column(name = "transaction_type")
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "comments")
    private String comment;

    @Column(name = "buy_transaction_ids_json")
    @Convert(converter = StringListConverter.class)
    private List<String> buyTransactionIds = new ArrayList<>();

    @Column(name = "sell_transaction_ids_json")
    @Convert(converter = StringListConverter.class)
    private List<String> sellTransactionIds = new ArrayList<>();

    @Column(name = "corporate_actions_json")
    @Convert(converter = CorporateActionListConverter.class)
    private List<CorporateActionEntity> corporateActions = new ArrayList<>();

    @Embedded
    @Setter(value = AccessLevel.NONE)
    private AuditColumns auditMetadata = new AuditColumns();

    /**
     * Guards the read-modify-write merge of a same-day buy into this lot (W-337e, D-949). MongoDB's multi-document
     * transactions caught two concurrent writers of the same lot automatically; PostgreSQL does not, so a lot now
     * carries its own optimistic lock, same as {@code ProfitAndLossEntity}'s.
     */
    @JsonIgnore
    @Version
    @Setter(value = AccessLevel.NONE)
    @Column(name = "version")
    private Long version;

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    // this email we can't accept from the request payload, we are formatting this through the code
    public static final String EMAIL = "email";

    public static Set<String> ALLOWED_FIELDS = Set.of(EMAIL, "transaction_date", "transaction_type",
            "account_type", "account_holder", "exchange_name", "stock_code", "broker_name", "asset_type");

}
