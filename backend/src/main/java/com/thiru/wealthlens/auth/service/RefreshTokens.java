package com.thiru.wealthlens.auth.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * Refresh tokens (W-358b, D-853 C3a): 14 days from the sign-in, rotated on each use, stored hashed, and ended by logout, a password
 * change and erasure. A token presented after it was spent means someone else holds a copy, so the whole family ends — the thief's
 * copy and the rightful holder's newer token alike — and the person signs in again.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class RefreshTokens {

    public static final Duration LIFETIME = Duration.ofDays(14);

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder TOKEN_TEXT = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** A new family, at sign-in; returns the token to hand out. */
    public String start(String email, Collection<String> factors) {
        Instant now = clock.instant();
        return store(email, UUID.randomUUID().toString(), List.copyOf(factors), now.plus(LIFETIME));
    }

    /**
     * Spends {@code presented} — once only, even under two concurrent calls — and returns its row. Refused as unknown, spent (the
     * family is then ended), ended or expired, all with the same answer, so a refusal says nothing about the token.
     */
    public RefreshToken spend(String presented) {
        String hash = hash(presented);
        Instant now = clock.instant();
        RefreshToken spent = mongoTemplate.findAndModify(query(where("tokenHash").is(hash).and("usedAt").is(null).and("revokedAt").is(null)),
                new Update().set("usedAt", now), RefreshToken.class);
        if (spent == null) {
            repository.findByTokenHash(hash).filter(token -> token.getUsedAt() != null).ifPresent(reused -> {
                log.warn("A spent refresh token was presented again; its family {} is ended", reused.getFamilyId());
                endFamily(reused.getFamilyId());
            });
            throw refused();
        }
        if (!spent.getExpiresAt().isAfter(now)) {
            throw refused();
        }
        return spent;
    }

    /** The token that follows {@code spent}: same family, same factors, same expiry. */
    public String succeed(RefreshToken spent) {
        return store(spent.getEmail(), spent.getFamilyId(), spent.getFactors(), spent.getExpiresAt());
    }

    /** Logout: ends the family of {@code presented}; an unknown token changes nothing, and the answer is the same. */
    public void end(String presented) {
        repository.findByTokenHash(hash(presented)).ifPresent(token -> endFamily(token.getFamilyId()));
    }

    public void endFamily(String familyId) {
        mongoTemplate.updateMulti(unrevoked(where("familyId").is(familyId)), new Update().set("revokedAt", clock.instant()), RefreshToken.class);
    }

    /** Every sign-in of {@code email}, as after a password change. */
    public void endAll(String email) {
        mongoTemplate.updateMulti(unrevoked(where("email").is(email)), new Update().set("revokedAt", clock.instant()), RefreshToken.class);
    }

    public void erase(String email) {
        repository.deleteByEmail(email);
    }

    public static BadCredentialsException refused() {
        return new BadCredentialsException("The refresh token is not valid; sign in again");
    }

    private static Query unrevoked(Criteria criteria) {
        return query(criteria.and("revokedAt").is(null));
    }

    private String store(String email, String familyId, List<String> factors, Instant expiresAt) {
        byte[] secret = new byte[TOKEN_BYTES];
        random.nextBytes(secret);
        String token = TOKEN_TEXT.encodeToString(secret);
        repository.save(new RefreshToken(null, hash(token), familyId, email, factors, clock.instant(), expiresAt, null, null));
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
