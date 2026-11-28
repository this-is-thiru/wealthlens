package com.thiru.wealthlens.taxplanning.jobs;

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

/** Something a job refused to do, and why ({@code DR 19} E8's dead letter): kept for the owner to act on. Never a value. */
@Entity
@Table(name = "tax_job_failures")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class JobFailure {

    @Id
    private String id;

    @Column(name = "job")
    private String job;

    @Column(name = "subject")
    private String subject;

    @Column(name = "reason")
    private String reason;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /** Who recorded it and when (W-337f, D-951 C7a). */
    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
