package com.thiru.wealthlens.corporate.entity;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.model.DemergerDetail;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Corporate action reference row on PostgreSQL (W-337d1, D-945; Flyway {@code V7__corporate_action.sql}).
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "corporate_action")
@EntityListeners(AuditingEntityListener.class)
public class CorporateActionEntity {

    @Id
    private String id;

    @Column(name = "stock_code")
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    @Column(name = "to_stock_code")
    private String toStockCode;

    @Column(name = "to_stock_name")
    private String toStockName;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private CorporateActionType type;

    @Column(name = "asset_type")
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(name = "description")
    private String description;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "ex_date")
    private LocalDate exDate;

    // A bonus share's holding period runs from its allotment, not the ex-date (W-088k4a, D-307).
    @Column(name = "allotment_date")
    private LocalDate allotmentDate;

    @Column(name = "priority")
    private int priority;

    @Column(name = "action_price")
    private String actionPrice;

    @Column(name = "ratio")
    private String ratio;

    @Embedded
    private DemergerDetail demergerDetail;

    @Embedded
    @Setter(value = AccessLevel.NONE)
    private AuditColumns auditMetadata = new AuditColumns();

    @PostLoad
    void clearEmptyDemergerDetail() {
        if (this.demergerDetail != null && this.demergerDetail.isEmpty()) {
            this.demergerDetail = null;
        }
    }

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
