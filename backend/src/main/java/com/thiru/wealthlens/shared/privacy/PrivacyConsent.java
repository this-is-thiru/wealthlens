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
 * The proof that a person was given the notice, and, where they registered after it existed, that they consented against it
 * (Digital Personal Data Protection Act, 2023 s.6(10); W-250; ported to PostgreSQL in W-337c2, D-943). One per account;
 * erased with it. Withdrawal is the account's erasure request (W-250 C3a), which {@link AccountErasureRequest} records.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "privacy_consents")
@EntityListeners(AuditingEntityListener.class)
public class PrivacyConsent {

    @Id
    private String id;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    /** The {@link PrivacyNotice#version()} the person was shown. */
    @Column(name = "notice_version")
    private String noticeVersion;

    /** UTC; when the person consented at registration (s.5(1)). Absent for an account registered before W-250. */
    @Column(name = "given_at")
    private LocalDateTime givenAt;

    /** UTC; when an account registered before W-250 was first given the notice, at a sign-in (s.5(2)(a)). */
    @Column(name = "noticed_at")
    private LocalDateTime noticedAt;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
