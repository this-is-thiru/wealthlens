package com.thiru.wealthlens.auth.entity;

import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One refresh token of a sign-in (W-358b, D-853 C3a). Only its SHA-256 hash is stored, so a copy of the collection signs nobody in.
 * Every token rotated from one sign-in shares its family and its expiry; presenting a spent token ends the family. Times are
 * {@link Instant}s because the expiry index reads the stored date as an instant.
 */
@Document(value = "refresh_tokens")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {

    @MongoId
    private String id;

    @Field("token_hash")
    @Indexed(name = "refresh_token_hash_idx", unique = true)
    private String tokenHash;

    @Field("family_id")
    @Indexed(name = "refresh_token_family_idx")
    private String familyId;

    @Field("email")
    @Indexed(name = "refresh_token_email_idx")
    @ValueConverter(SearchableIdentifier.class)
    private String email;

    /** The factors the sign-in proved ({@code FACTOR_PASSWORD}), carried into every access token the family issues. */
    @Field("factors")
    private List<String> factors;

    @Field("issued_at")
    private Instant issuedAt;

    /** The sign-in's expiry, kept by every rotation; Mongo removes the row once it passes. */
    @Field("expires_at")
    @Indexed(name = "refresh_token_expiry_idx", expireAfter = "0s")
    private Instant expiresAt;

    @Field("used_at")
    private Instant usedAt;

    @Field("revoked_at")
    private Instant revokedAt;
}
