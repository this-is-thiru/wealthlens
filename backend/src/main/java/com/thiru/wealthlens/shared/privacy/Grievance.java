package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.protection.EncryptedField;
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
 * A user's grievance about how their personal data is handled, and its answer (Digital Personal Data Protection Act, 2023
 * s.13; W-237b). Only facts are stored: when it was received, the date promised at that moment, and when it was answered.
 * Whether it is overdue is read from those dates, never stored.
 */
@Data
@NoArgsConstructor
@Document("privacy_grievances")
public class Grievance implements AuditableEntity {

    @MongoId
    private String id;

    @Indexed
    @Field("email")
    @ValueConverter(SearchableIdentifier.class)
    private String email;

    @ValueConverter(EncryptedField.class)
    @Field("text")
    private String text;

    /** UTC. */
    @Field("received_at")
    private LocalDateTime receivedAt;

    /** UTC; the period published when it was received, counted from then, so a later change of period breaks no promise. */
    @Field("respond_by")
    private LocalDateTime respondBy;

    @ValueConverter(EncryptedField.class)
    @Field("answer")
    private String answer;

    /** UTC; absent until answered. */
    @Field("answered_at")
    private LocalDateTime answeredAt;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
