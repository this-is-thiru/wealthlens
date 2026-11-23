package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.FundCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.PlanType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
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
 * Charges belonging to an instrument rather than to a broker, plus scheme attributes rules need
 * (ported to PostgreSQL in W-337d2, D-947; Flyway {@code V8__brokercharges.sql}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "charge_instruments")
@EntityListeners(AuditingEntityListener.class)
public class ChargeInstrumentEntity {

    @JsonIgnore
    @Id
    private String id;

    @Column(name = "stock_code")
    private String stockCode;

    @Column(name = "isin")
    private String isin;

    @Column(name = "name")
    private String name;

    @Column(name = "asset_type")
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(name = "fund_category")
    @Enumerated(EnumType.STRING)
    private FundCategory fundCategory;

    @Column(name = "equity_oriented")
    private Boolean equityOriented;

    @Column(name = "plan_type")
    @Enumerated(EnumType.STRING)
    private PlanType planType;

    @Column(name = "amc")
    private String amc;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Null means open-ended. See {@link ChargeScheduleEntity} on validity versus status. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private EntityStatus status;

    /** Scheme-level charges: exit load, and any fee the fund rather than the broker levies. */
    @Column(name = "rules_json")
    @Convert(converter = ChargeRuleListConverter.class)
    private List<ChargeRule> rules = new ArrayList<>();

    @Column(name = "source_url")
    private String sourceUrl;

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
