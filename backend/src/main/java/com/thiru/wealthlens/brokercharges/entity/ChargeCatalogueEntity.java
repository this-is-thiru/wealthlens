package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The registry of valid charge codes (ported to PostgreSQL in W-337d2, D-947; Flyway {@code V4__charge_catalogue.sql}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "charge_catalogue")
@EntityListeners(AuditingEntityListener.class)
public class ChargeCatalogueEntity {

    @JsonIgnore
    @Id
    private String id;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private ChargeCategory category;

    @Column(name = "description")
    private String description;

    /** The statute, circular or exchange notice the charge derives from. */
    @Column(name = "statutory_reference")
    private String statutoryReference;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private EntityStatus status;

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
