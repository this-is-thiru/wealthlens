package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.entity.RefreshToken;
import com.thiru.wealthlens.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens (W-358b, D-853 C3a; ported to PostgreSQL in W-337c1, D-941): 14 days from the sign-in, rotated on each
 * use, stored hashed, and ended by logout, a password change and erasure. A token presented after it was spent means
 * someone else holds a copy, so the whole family ends — the thief's copy and the rightful holder's newer token alike —
 * and the person signs in again. Expired tokens are swept on each sign-in (D-937 C3a).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class RefreshTokens {

    public static final Duration LIFETIME = Duration.ofDays(14);

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder TOKEN_TEXT = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** A new family, at sign-in; sweeps expired tokens (D-937 C3a) and returns the token to hand out. */
    @Transactional("postgresTransactionManager")
    public String start(String email, Collection<String> factors) {
        Instant now = clock.instant();
        repository.deleteExpired(now);
        return store(email, UUID.randomUUID().toString(), List.copyOf(factors), now, now.plus(LIFETIME));
    }

    /**
     * Spends {@code presented} — once only, even under two concurrent calls — and returns its row. Refused as unknown, spent (the
     * family is then ended), ended or expired, all with the same answer, so a refusal says nothing about the token.
     * {@code noRollbackFor = BadCredentialsException.class} ensures family revocation on reuse commits even as the refusal is thrown.
     */
    @Transactional(transactionManager = "postgresTransactionManager", noRollbackFor = BadCredentialsException.class)
    public RefreshToken spend(String presented) {
        String hash = hash(presented);
        Instant now = clock.instant();
        if (repository.spend(hash, now) == 0) {
            repository.findByTokenHash(hash).filter(token -> token.getUsedAt() != null).ifPresent(reused -> {
                log.warn("A spent refresh token was presented again; its family {} is ended", reused.getFamilyId());
                repository.endFamily(reused.getFamilyId(), now);
            });
            throw refused();
        }
        RefreshToken spent = repository.findByTokenHash(hash).orElseThrow(RefreshTokens::refused);
        if (!spent.getExpiresAt().isAfter(now)) {
            throw refused();
        }
        return spent;
    }

    /** The token that follows {@code spent}: same family, same factors, same expiry. */
    @Transactional("postgresTransactionManager")
    public String succeed(RefreshToken spent) {
        return store(spent.getEmail(), spent.getFamilyId(), spent.getFactors(), clock.instant(), spent.getExpiresAt());
    }

    /** Logout: ends the family of {@code presented}; an unknown token changes nothing, and the answer is the same. */
    @Transactional("postgresTransactionManager")
    public void end(String presented) {
        repository.findByTokenHash(hash(presented)).ifPresent(token -> repository.endFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional("postgresTransactionManager")
    public void endFamily(String familyId) {
        repository.endFamily(familyId, clock.instant());
    }

    /** Every sign-in of {@code email}, as after a password change. */
    @Transactional("postgresTransactionManager")
    public void endAll(String email) {
        repository.endAll(email, clock.instant());
    }

    @Transactional("postgresTransactionManager")
    public void erase(String email) {
        repository.deleteByEmail(email);
    }

    public static BadCredentialsException refused() {
        return new BadCredentialsException("The refresh token is not valid; sign in again");
    }

    private String store(String email, String familyId, List<String> factors, Instant issuedAt, Instant expiresAt) {
        byte[] secret = new byte[TOKEN_BYTES];
        random.nextBytes(secret);
        String token = TOKEN_TEXT.encodeToString(secret);
        repository.save(new RefreshToken(UUID.randomUUID().toString(), hash(token), familyId, email, factors, issuedAt, expiresAt, null, null));
        return token;
    }

    private static String hash(String token) {
        try {
            return TOKEN_TEXT.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
        }
    }
}
