package com.thiru.wealthlens.shared.privacy;

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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A user's request to erase their whole account (Digital Personal Data Protection Act, 2023 s.12(3); W-237d C2a;
 * ported to PostgreSQL in W-337c2, D-943). Only facts are stored: when it was asked and when it was done. It is kept
 * after the erasure, apart from the login it erases, because it is the record that the erasure was done.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "privacy_erasure_requests")
@EntityListeners(AuditingEntityListener.class)
public class AccountErasureRequest {

    @Id
    private String id;

    @Column(name = "email", nullable = false)
    @Convert(converter = SearchableColumn.class)
    private String email;

    /** UTC; the last processing, from which Rules r.8(3)'s year is counted; for a lapse, the moment the purpose lapsed. */
    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    /** Absent on a request stored before W-249, which was asked for: read it with {@link #getCause()}. */
    @Column(name = "cause")
    @Enumerated(EnumType.STRING)
    private ErasureCause cause;

    /** UTC; absent until every holder has erased. */
    @Column(name = "erased_at")
    private LocalDateTime erasedAt;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    public ErasureCause getCause() {
        return Optional.ofNullable(cause).orElse(ErasureCause.REQUESTED);
    }

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
