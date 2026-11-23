package com.thiru.wealthlens.auth.repository;

import com.thiru.wealthlens.auth.entity.RefreshTokenJpa;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link RefreshTokenRepository}'s PostgreSQL counterpart, proving the mapping (W-337a, D-937). {@link #spend} replaces
 * Mongo's {@code findAndModify} atomic compare-and-set: the {@code WHERE} clause only matches an unused, unrevoked token, so
 * two concurrent calls on the same row serialise under Postgres' row lock and exactly one reports a row changed (D-937 C2a).
 * {@link #deleteExpired} replaces the TTL index Postgres has no equivalent of (D-937 C3a): swept on each sign-in.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpa, String> {

    Optional<RefreshTokenJpa> findByTokenHash(String tokenHash);

    void deleteByEmail(String email);

    @Modifying
    @Query("UPDATE RefreshTokenJpa t SET t.usedAt = :now WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int spend(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshTokenJpa t WHERE t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
