package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.protection.EncryptedColumn;
import com.thiru.wealthlens.shared.protection.SearchableColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One request that reached a person's data, app-wide: who asked, whose data, by which route, with what result, from where and
 * when — never a value from the path, the query or the body (W-237e C1a; ported to PostgreSQL in W-337c2, D-943). It is the
 * log the Digital Personal Data Protection Rules, 2025 r.6(1)(c) ask for, so that unauthorised access can be detected and
 * investigated. Appended only; erased a year after it was written ({@link LogRetention}), or with its principal's account
 * (W-237e C3a).
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "privacy_processing_log")
public class ProcessingRecord {

    @Id
    private String id;

    /** The signed-in user, or the email a login was attempted for. */
    @Column(name = "principal")
    @Convert(converter = SearchableColumn.class)
    private String principal;

    /** The person whose data the request reached: the path's {@code {email}}, else the principal. */
    @Column(name = "subject_email")
    @Convert(converter = SearchableColumn.class)
    private String subjectEmail;

    @Column(name = "method")
    private String method;

    /** The matched route pattern; absent when the request was refused before it reached a controller. */
    @Column(name = "route")
    private String route;

    @Column(name = "status")
    private int status;

    /** The address the request came from: r.8(3)'s "associated traffic data". */
    @Column(name = "remote_address")
    @Convert(converter = EncryptedColumn.class)
    private String remoteAddress;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    /**
     * The id its answer carried as {@code X-Request-Id}, which every log line written while serving it names (W-358d1): the
     * logs mask emails, so this is what ties a line to whom it concerned. Rows written before W-358d1 have none.
     */
    @Column(name = "request_id")
    private String requestId;

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
