package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A user's request to erase their whole account (Digital Personal Data Protection Act, 2023 s.12(3); W-237d C2a). Only facts
 * are stored: when it was asked and when it was done. It is kept after the erasure, apart from the login it erases, because it
 * is the record that the erasure was done.
 */
@Data
@NoArgsConstructor
@Document("privacy_erasure_requests")
public class AccountErasureRequest implements AuditableEntity {

    @MongoId
    private String id;

    @Indexed
    @Field("email")
    private String email;

    /** UTC; the last processing, from which Rules r.8(3)'s year is counted; for a lapse, the moment the purpose lapsed. */
    @Field("requested_at")
    private LocalDateTime requestedAt;

    /** Absent on a request stored before W-249, which was asked for: read it with {@link #getCause()}. */
    @Field("cause")
    private ErasureCause cause;

    /** UTC; absent until every holder has erased. */
    @Field("erased_at")
    private LocalDateTime erasedAt;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();

    public ErasureCause getCause() {
        return Optional.ofNullable(cause).orElse(ErasureCause.REQUESTED);
    }
}
