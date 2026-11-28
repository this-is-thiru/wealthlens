package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * What a user was charged for one event, broken down line by line
 * (ported to PostgreSQL in W-337d2, D-947; Flyway {@code V8__brokercharges.sql}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_charges")
@EntityListeners(AuditingEntityListener.class)
public class UserChargeEntity {

    @JsonIgnore
    @Id
    private String id;

    @Column(name = "email", nullable = false)
    private String email;

    /** Whose demat account the trade settled in. Part of every deduplication scope. */
    @Column(name = "account_holder")
    private String accountHolder;

    @Column(name = "broker_name")
    @Enumerated(EnumType.STRING)
    private BrokerName brokerName;

    @Column(name = "asset_type")
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(name = "segment")
    @Enumerated(EnumType.STRING)
    private TradeSegment segment;

    @Column(name = "exchange")
    private String exchange;

    @Column(name = "stock_code")
    private String stockCode;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "event")
    @Enumerated(EnumType.STRING)
    private ChargeEvent event;

    /** When the trade happened. */
    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    /** When the charge was worked out — long after the trade, for a backfilled quarter. */
    @Column(name = "computed_on")
    private LocalDateTime computedOn;

    /** Why these lines, or why none. */
    @Column(name = "resolution")
    @Enumerated(EnumType.STRING)
    private ChargeResolution resolution;

    /** Which rate card produced this. Lets a corrected card find every row it touched. */
    @Column(name = "schedule_id")
    private String scheduleId;

    @Column(name = "schedule_code")
    private String scheduleCode;

    /** Which statutory card priced the law's lines, where one did (D-380). Absent on rows written before. */
    @Column(name = "statutory_schedule_id")
    private String statutoryScheduleId;

    @Column(name = "statutory_schedule_code")
    private String statutoryScheduleCode;

    /** Which instrument profile contributed scheme-level lines, where one did. */
    @Column(name = "instrument_id")
    private String instrumentId;

    @Column(name = "turnover")
    private double turnover;

    @Column(name = "quantity")
    private double quantity;

    /** The contract note. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_charge_lines", joinColumns = @JoinColumn(name = "user_charge_id"))
    @OrderColumn(name = "line_order")
    private List<ChargeLine> lines = new ArrayList<>();

    /** Denormalised from {@code lines} so reports and deduplication queries read by charge code directly. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_charge_amounts", joinColumns = @JoinColumn(name = "user_charge_id"))
    @MapKeyColumn(name = "charge_code")
    @Column(name = "amount")
    private Map<String, Double> amountByCode = new HashMap<>();

    @Column(name = "total_charges")
    private double totalCharges;

    @Embedded
    @Setter(value = AccessLevel.NONE)
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
