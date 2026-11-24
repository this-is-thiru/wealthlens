package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
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
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A broker's rate card, valid over a date range for a scope (ported to PostgreSQL in W-337d2, D-947;
 * Flyway {@code V8__brokercharges.sql}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "charge_schedules")
@EntityListeners(AuditingEntityListener.class)
public class ChargeScheduleEntity {

    @JsonIgnore
    @Id
    private String id;

    /** Human-readable key, for example {@code ZERODHA_EQ_DELIVERY_2025_04}. */
    @Column(name = "schedule_code", unique = true, nullable = false)
    private String scheduleCode;

    @Column(name = "broker_name")
    @Enumerated(EnumType.STRING)
    private BrokerName brokerName;

    /** Null matches any asset type. */
    @Column(name = "asset_type")
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    /** Null matches any segment. */
    @Column(name = "segment")
    @Enumerated(EnumType.STRING)
    private TradeSegment segment;

    /** Null matches any exchange. NSE and BSE levy different transaction charges. */
    @Column(name = "exchange")
    private String exchange;

    /** Null matches any plan. Reserved for per-user negotiated rates. */
    @Column(name = "plan_code")
    private String planCode;

    /** Inclusive. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** Inclusive. Null means open-ended, and marks this as the current card for its scope. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private EntityStatus status;

    @Column(name = "requires_instrument_profile")
    private boolean requiresInstrumentProfile;

    @Column(name = "rules_json")
    @Convert(converter = ChargeRuleListConverter.class)
    private List<ChargeRule> rules = new ArrayList<>();

    /** The broker's published charges page, so a human can re-verify the rates. */
    @Column(name = "source_url")
    private String sourceUrl;

    /** When a human last checked these rates. Null means unverified, and is warned about at startup. */
    @Column(name = "verified_on")
    private LocalDate verifiedOn;

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
