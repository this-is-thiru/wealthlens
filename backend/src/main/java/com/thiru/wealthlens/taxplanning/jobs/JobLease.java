package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Which run of a job holds it ({@code DR 19} E8): keyed by the job's name, so at most one exists; a lease past {@code expiresAt}
 * may be taken over. {@code holder} is unique per run, so a run overtaken while it was slow sees that it no longer holds the
 * lease and stops. An {@link Instant}, not a local time, so the comparison against "now" is the same moment whatever the
 * server's zone. No TTL index (W-337f, D-951 C6a): an abandoned lease is reclaimed lazily by the next
 * acquire's {@code expires_at < now} check, never swept proactively — same
 * precedent as {@code refresh_tokens} (D-937 C3a).
 */
@Entity
@Table(name = "tax_job_leases")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class JobLease {

    @Id
    @Column(name = "job")
    private String job;

    @Column(name = "holder")
    private String holder;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Who took it and when (W-337f, D-951 C7a). */
    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();
}
