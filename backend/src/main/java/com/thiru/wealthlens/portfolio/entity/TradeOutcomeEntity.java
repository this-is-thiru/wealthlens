package com.thiru.wealthlens.portfolio.entity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Indexed on {@code {email, sell_date}} because every read is one user's realised trades, and a capital-gains
 * statement wants them in date order; {@code trade_outcome_buy_lot_idx} is every realised row that consumed a
 * given buy lot, backing the per-holding charge view. Both indexes are declared in
 * {@code db/migration/portfolio/V9__portfolio.sql} (W-337e, D-949) — Postgres indexes apply at DDL time, unlike
 * {@code PortfolioIndexInitializer}'s old Mongo {@code createIndex} calls, which this port removes.
 */
@Entity
@Table(name = "trade_outcomes")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TradeOutcomeEntity {

    @JsonIgnore
    @Id
    private String id;

    // Identity fields
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

    @Column(name = "asset_type")
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(name = "account_type")
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(name = "account_holder")
    @Convert(converter = SearchableColumn.class)
    private String accountHolder;

    // Buy side
    @Column(name = "original_buy_price")
    private double originalBuyPrice;

    /**
     * The buy price after corporate-action adjustment — bonus, split, demerger.
     *
     * <p>"CA" here is <b>corporate action</b>, never chartered accountant. The stored field keeps its
     * original abbreviated name because live documents carry it; the Java name spells it out.
     */
    @Column(name = "ca_adjusted_buy_price")
    private double corporateActionAdjustedBuyPrice;

    @Column(name = "buy_quantity")
    private Double buyQuantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Column(name = "buy_date")
    private LocalDate buyDate;

    @Column(name = "buy_broker_charges")
    private double buyBrokerCharges;

    @Column(name = "buy_misc_charges")
    private double buyMiscCharges;

    // Sell side
    @Column(name = "sell_price")
    private double sellPrice;

    @Column(name = "sell_quantity")
    private Double sellQuantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Column(name = "sell_date")
    private LocalDate sellDate;

    @Column(name = "sell_broker_charges")
    private double sellBrokerCharges;

    @Column(name = "sell_misc_charges")
    private double sellMiscCharges;

    // Computed fields
    @Column(name = "total_buy_value")
    private double totalBuyValue;

    @Column(name = "total_sell_value")
    private double totalSellValue;

    @Column(name = "net_profit")
    private double netProfit;

    @Column(name = "profit_percentage")
    private double profitPercentage;

    @Column(name = "holding_period_days")
    private Long holdingPeriodDays;

    /**
     * How the disposal was traded. Decides speculative and derivative treatment before holding
     * period is consulted at all (D1), so a row without it cannot be classified after the fact.
     */
    @Column(name = "segment")
    @Enumerated(EnumType.STRING)
    private TradeSegment segment;

    /**
     * The instrument's sub-class — {@code EQUITY_ORIENTED} / {@code SPECIFIED} / {@code OTHER} for a
     * scheme, {@code LISTED} / {@code UNLISTED} for a bond.
     *
     * <p>Null until the instrument registry ships (D7). Stored explicitly rather than inferred so a
     * reader can tell a row that was classified as unknown from one written before the field existed.
     */
    @Column(name = "instrument_sub_class")
    private String instrumentSubClass;

    /**
     * What the buy actually cost, by charge code, rather than one lumped total.
     *
     * <p>Kept by code because which charges reduce a capital gain is decided per code, by the tax side's rule data
     * for the year (W-162a, D-376). A single total cannot be split after the fact.
     */
    @Column(name = "buy_charge_breakup_json")
    @Convert(converter = StringDoubleMapConverter.class)
    private Map<String, Double> buyChargeBreakup = new LinkedHashMap<>();

    /** The same for the sell side, pro-rated to this lot's share of the disposal. */
    @Column(name = "sell_charge_breakup_json")
    @Convert(converter = StringDoubleMapConverter.class)
    private Map<String, Double> sellChargeBreakup = new LinkedHashMap<>();

    @Column(name = "financial_year")
    private String financialYear;

    // Linkage fields for traceability
    @Column(name = "source_sell_transaction_id")
    private String sourceSellTransactionId;

    @Column(name = "source_buy_lot_id")
    private String sourceBuyLotId;

    // CA tracking
    /**
     * Whether this holding arose from a corporate action rather than a purchase — a bonus allotment
     * or a split, which are issued free and so have no acquisition cost of their own.
     *
     * <p>"CA" here is <b>corporate action</b>. The stored field keeps its abbreviated name because
     * live documents carry it; the Java name spells it out.
     */
    @Column(name = "is_ca_derived")
    private Boolean corporateActionDerived;

    @Column(name = "applied_corporate_actions_json")
    @Convert(converter = CorporateActionListConverter.class)
    private List<CorporateActionEntity> appliedCorporateActions = new ArrayList<>();

    /**
     * How the lot came to be held, from the lot's own record. Unlike {@link #corporateActionDerived}, which a purchase
     * later split also sets, this is the lot's origin; absent on rows written before W-088f (D-249).
     */
    @Column(name = "acquisition_mode")
    @Enumerated(EnumType.STRING)
    private AcquisitionMode acquisitionMode;

    /** The corporate action that created the lot, carried as itself; set only when {@link #acquisitionMode} says so. */
    @Column(name = "acquisition_corporate_action")
    @Enumerated(EnumType.STRING)
    private CorporateActionType acquisitionCorporateAction;

    /**
     * The exchange the lot's buy was recorded on, from the lot's own record — not {@link #exchangeName}, which the
     * writers take from different sides; absent on rows written before W-088j2b (D-295).
     */
    @Column(name = "buy_exchange_name")
    private String buyExchangeName;

    /** The segment the lot's buy was recorded in; absent on rows written before W-088j2b. */
    @Column(name = "buy_segment")
    @Enumerated(EnumType.STRING)
    private TradeSegment buySegment;

    /** The exchange the sale was recorded on; absent on rows written before W-088j2b. */
    @Column(name = "sell_exchange_name")
    private String sellExchangeName;

    /** The segment the sale was recorded in; absent on rows written before W-088j2b. */
    @Column(name = "sell_segment")
    @Enumerated(EnumType.STRING)
    private TradeSegment sellSegment;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
