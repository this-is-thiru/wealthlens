package com.thiru.wealthlens.taxplanning.protection;

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
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One read of special-category data, recorded even though nothing changed ({@code DR 14} O1.1, O4): who, what, when — never a
 * value. Appended only; nothing updates one, and only the erasure of what it names deletes it (W-235 C4a) — the hold before an
 * erasure is the year the Data Protection Rules, 2025 r.6(1)(e) keep such logs.
 */
@Entity
@Table(name = "tax_access_log")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class AccessRecord {

    @Id
    private String id;

    @Column(name = "principal")
    private String principal;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject")
    private ReadSubject subject;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "read_at")
    private Instant readAt;

    /** Who wrote it and when (W-337f, D-951 C7a). */
    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
