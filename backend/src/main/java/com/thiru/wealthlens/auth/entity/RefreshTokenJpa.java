package com.thiru.wealthlens.auth.entity;

import com.thiru.wealthlens.shared.protection.SearchableColumn;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Data;

/**
 * {@link RefreshToken}'s PostgreSQL counterpart (W-337a, D-937): a parallel JPA mapping proving the probe, written beside
 * the live MongoDB entity rather than replacing it (the real cutover is W-337c's). {@code factors} becomes a child table
 * ({@code refresh_token_factors}) rather than an embedded array, since Postgres has no native embedded-document form; the
 * expiry has no TTL index equivalent, so it is swept on each sign-in instead ({@code W-337aRefreshTokenJpaRepository},
 * D-937 C3a).
 */
@Entity
@Table(name = "refresh_tokens")
@Data
public class RefreshTokenJpa {

    @Id
    private String id;

    @Column(name = "token_hash", unique = true, nullable = false)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "email", nullable = false)
    @Convert(converter = SearchableColumn.class)
    private String email;

    @ElementCollection
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
}
