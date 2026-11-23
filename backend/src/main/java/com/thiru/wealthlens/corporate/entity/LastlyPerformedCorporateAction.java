package com.thiru.wealthlens.corporate.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.protection.SearchableColumn;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Tracks the latest corporate action date performed per user, stock, asset type, action type, and broker
 * (ported to PostgreSQL in W-337d1, D-945).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "lastly_performed_corporate_action")
@EntityListeners(AuditingEntityListener.class)
public class LastlyPerformedCorporateAction {

    @JsonIgnore
    @Id
    private String id;

    @Column(name = "email", nullable = false)
    @Convert(converter = SearchableColumn.class)
    private String email;

    @Column(name = "stock_code")
    private String stockCode;

    @Column(name = "asset_type")
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(name = "action_type")
    @Enumerated(EnumType.STRING)
    private CorporateActionType actionType;

    @Column(name = "action_date")
    private LocalDate actionDate;

    @Column(name = "broker_name")
    @Enumerated(EnumType.STRING)
    private BrokerName brokerName;

    @Embedded
    @Setter(value = AccessLevel.NONE)
    @Builder.Default
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
