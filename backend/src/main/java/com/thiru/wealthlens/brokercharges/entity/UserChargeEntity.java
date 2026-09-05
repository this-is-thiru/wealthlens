package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * What a user was charged for one event, broken down line by line.
 *
 * <p>This is the source of truth for charges. The profit-and-loss charge hierarchy is a derived
 * projection of it: the live path accumulates incrementally for speed, but a recomputation rebuilds
 * the affected financial year from these rows rather than applying deltas, because a contribution
 * already folded into a sum cannot be reliably subtracted.
 *
 * <p>A row is written <strong>even when nothing is charged</strong>, carrying the reason in
 * {@code resolution}. Backfilling several years crosses periods with no rate card on file, and a
 * warning in a log scrolls away long before anyone notices the gap.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "user_charges")
@CompoundIndexes({
    /** One row per transaction: a re-uploaded quarter replaces rather than duplicates. */
    @CompoundIndex(name = "user_charge_txn_idx", def = "{'email': 1, 'transaction_id': 1}", unique = true),
    /**
     * Depository-charge deduplication. {@code account_holder} is part of the key because a charge
     * is levied per demat account: the same scrip sold on the same day under two holders incurs two
     * separate debits.
     */
    @CompoundIndex(name = "user_charge_dedupe_idx",
            def = "{'email': 1, 'account_holder': 1, 'broker_name': 1, 'stock_code': 1, 'transaction_date': 1}"),
    /** Charge history and the gaps report. */
    @CompoundIndex(name = "user_charge_history_idx", def = "{'email': 1, 'transaction_date': -1}")
})
public class UserChargeEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    private String id;

    @Field("email")
    private String email;

    /** Whose demat account the trade settled in. Part of every deduplication scope. */
    @Field("account_holder")
    private String accountHolder;

    @Field(name = "broker_name", targetType = FieldType.STRING)
    private BrokerName brokerName;

    @Field(name = "asset_type", targetType = FieldType.STRING)
    private AssetType assetType;

    @Field(name = "segment", targetType = FieldType.STRING)
    private TradeSegment segment;

    @Field("exchange")
    private String exchange;

    @Field("stock_code")
    private String stockCode;

    @Field("transaction_id")
    private String transactionId;

    @Field("order_id")
    private String orderId;

    @Field(name = "event", targetType = FieldType.STRING)
    private ChargeEvent event;

    /** When the trade happened. */
    @Field("transaction_date")
    private LocalDate transactionDate;

    /** When the charge was worked out — long after the trade, for a backfilled quarter. */
    @Field("computed_on")
    private LocalDateTime computedOn;

    /** Why these lines, or why none. */
    @Field(name = "resolution", targetType = FieldType.STRING)
    private ChargeResolution resolution;

    /** Which rate card produced this. Lets a corrected card find every row it touched. */
    @Field("schedule_id")
    private String scheduleId;

    @Field("schedule_code")
    private String scheduleCode;

    /** Which instrument profile contributed scheme-level lines, where one did. */
    @Field("instrument_id")
    private String instrumentId;

    @Field("turnover")
    private double turnover;

    @Field("quantity")
    private double quantity;

    /** The contract note. */
    @Field("lines")
    private List<ChargeLine> lines = new ArrayList<>();

    /** Denormalised from {@code lines} so reports aggregate without unwinding. */
    @Field("amount_by_code")
    private Map<String, Double> amountByCode = new HashMap<>();

    @Field("total_charges")
    private double totalCharges;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
