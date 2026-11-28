package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A personal data breach as the owner reports it (Digital Personal Data Protection Act, 2023 s.8(6); Rules r.7; W-237c;
 * ported to PostgreSQL in W-337c2, D-943): what each affected user is told (r.7(1)(a) – (d)), who they are, and when each
 * step towards the Board was taken. Only facts with their times are stored; whether a step is late is read from them.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "privacy_breach_notices")
@EntityListeners(AuditingEntityListener.class)
public class BreachNotice {

    @Id
    private String id;

    @Column(name = "description")
    private String description;

    @Column(name = "consequences")
    private String consequences;

    @Column(name = "mitigation")
    private String mitigation;

    @Column(name = "safety_measures")
    private String safetyMeasures;

    /** UTC, as the owner states it. */
    @Column(name = "became_aware_at")
    private LocalDateTime becameAwareAt;

    @Column(name = "scope")
    @Enumerated(EnumType.STRING)
    private BreachScope scope;

    /** The emails the notice was posted to, resolved when it was posted. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "privacy_breach_notice_affected", joinColumns = @JoinColumn(name = "notice_id"))
    @Column(name = "affected", nullable = false)
    private List<String> affected = new ArrayList<>();

    /** UTC. */
    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    /** UTC; awareness plus the configured hours, or the later time the Board allowed. */
    @Column(name = "board_report_due_at")
    private LocalDateTime boardReportDueAt;

    /** The Board's reference for a longer period it allowed, absent unless one was. */
    @Column(name = "board_extension_reference")
    private String boardExtensionReference;

    /** UTC; absent until recorded. */
    @Column(name = "board_first_intimated_at")
    private LocalDateTime boardFirstIntimatedAt;

    /** UTC; absent until recorded. */
    @Column(name = "board_report_sent_at")
    private LocalDateTime boardReportSentAt;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
