package com.thiru.wealthlens.portfolio.entity;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.InstrumentAliasKind;
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
 * One value a broker's file writes for an instrument, and the stock code the app keeps its trades under (W-440b, D-1055). Unique on
 * kind, broker and value ({@code db/migration/portfolio/V15__instrument_aliases.sql}), so a value names one code.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "instrument_aliases")
@EntityListeners(AuditingEntityListener.class)
public class InstrumentAliasEntity {

    @Id
    private String id;

    @Column(name = "kind", nullable = false)
    @Enumerated(EnumType.STRING)
    private InstrumentAliasKind kind;

    /** Null for an ISIN, which is the instrument's everywhere. */
    @Column(name = "broker_name")
    @Enumerated(EnumType.STRING)
    private BrokerName brokerName;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

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
