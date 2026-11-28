package com.thiru.wealthlens.auth.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One refresh token of a sign-in (W-358b, D-853 C3a; ported to PostgreSQL in W-337c1, D-941). Only its SHA-256 hash is
 * stored, so a copy of the table signs nobody in. Every token rotated from one sign-in shares its family and its expiry;
 * presenting a spent token ends the family. Expired rows are swept on each sign-in (D-937 C3a).
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {

    @Id
    private String id;

    @Column(name = "token_hash", unique = true, nullable = false)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "email", nullable = false)
    private String email;

    /** Eager so callers outside the spend transaction can read the factors carried into the new access token. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "refresh_token_factors", joinColumns = @JoinColumn(name = "refresh_token_id"))
    @Column(name = "factor")
    private List<String> factors;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
