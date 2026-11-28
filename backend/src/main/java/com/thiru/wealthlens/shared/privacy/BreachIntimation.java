package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.Column;
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
 * One affected user told of one breach, by each mode and when (W-237c; ported to PostgreSQL in W-337c2, D-943): the
 * facts the Board's report on intimations needs (DPDP Rules r.7(2)(b)(vi)).
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "privacy_breach_intimations")
@EntityListeners(AuditingEntityListener.class)
public class BreachIntimation {

    @Id
    private String id;

    @Column(name = "notice_id")
    private String noticeId;

    @Column(name = "email")
    private String email;

    /** UTC; when the notice appeared in the user's account. */
    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    /** UTC; when the user first read it, absent until then. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** UTC; when the owner recorded sending it by email, absent until then. */
    @Column(name = "emailed_at")
    private LocalDateTime emailedAt;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
