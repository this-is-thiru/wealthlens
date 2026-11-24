package com.thiru.wealthlens.auth.repository;

import com.thiru.wealthlens.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Refresh token store on PostgreSQL (W-337c1, D-941). {@link #spend} replaces Mongo's {@code findAndModify} atomic
 * compare-and-set: the {@code WHERE} clause only matches an unused, unrevoked row, so two concurrent calls serialise
 * under Postgres' row lock and exactly one reports a row changed (D-937 C2a). {@link #deleteExpired} replaces the TTL
 * index (D-937 C3a).
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.usedAt = :now WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int spend(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int endFamily(@Param("familyId") String familyId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.email = :email AND t.revokedAt IS NULL")
    int endAll(@Param("email") String email, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
