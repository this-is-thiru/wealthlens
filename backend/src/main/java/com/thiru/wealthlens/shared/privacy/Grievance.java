package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.protection.EncryptedColumn;
import com.thiru.wealthlens.shared.protection.SearchableColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A user's grievance about how their personal data is handled, and its answer (Digital Personal Data Protection Act, 2023
 * s.13; W-237b; ported to PostgreSQL in W-337c2, D-943). Only facts are stored: when it was received, the date promised at
 * that moment, and when it was answered. Whether it is overdue is read from those dates, never stored.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "privacy_grievances")
@EntityListeners(AuditingEntityListener.class)
public class Grievance {

    @Id
    private String id;

    @Column(name = "email")
    @Convert(converter = SearchableColumn.class)
    private String email;

    @Column(name = "text")
    @Convert(converter = EncryptedColumn.class)
    private String text;

    /** UTC. */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** UTC; the period published when it was received, counted from then, so a later change of period breaks no promise. */
    @Column(name = "respond_by")
    private LocalDateTime respondBy;

    @Column(name = "answer")
    @Convert(converter = EncryptedColumn.class)
    private String answer;

    /** UTC; absent until answered. */
    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
