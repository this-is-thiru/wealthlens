package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * The proof that a person was given the notice, and, where they registered after it existed, that they consented against it
 * (Digital Personal Data Protection Act, 2023 s.6(10); W-250). One per account; erased with it. Withdrawal is the account's
 * erasure request (W-250 C3a), which {@link AccountErasureRequest} records.
 */
@Data
@NoArgsConstructor
@Document("privacy_consents")
public class PrivacyConsent implements AuditableEntity {

    @MongoId
    private String id;

    @Indexed(unique = true)
    @Field("email")
    @ValueConverter(SearchableIdentifier.class)
    private String email;

    /** The {@link PrivacyNotice#version()} the person was shown. */
    @Field("notice_version")
    private String noticeVersion;

    /** UTC; when the person consented at registration (s.5(1)). Absent for an account registered before W-250. */
    @Field("given_at")
    private LocalDateTime givenAt;

    /** UTC; when an account registered before W-250 was first given the notice, at a sign-in (s.5(2)(a)). */
    @Field("noticed_at")
    private LocalDateTime noticedAt;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
