package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One affected user told of one breach, by each mode and when (W-237c): the facts the Board's report on intimations
 * needs (DPDP Rules r.7(2)(b)(vi)).
 */
@Data
@NoArgsConstructor
@Document("privacy_breach_intimations")
public class BreachIntimation implements AuditableEntity {

    @MongoId
    private String id;

    @Indexed
    @Field("notice_id")
    private String noticeId;

    @Indexed
    @Field("email")
    private String email;

    /** UTC; when the notice appeared in the user's account. */
    @Field("posted_at")
    private LocalDateTime postedAt;

    /** UTC; when the user first read it, absent until then. */
    @Field("read_at")
    private LocalDateTime readAt;

    /** UTC; when the owner recorded sending it by email, absent until then. */
    @Field("emailed_at")
    private LocalDateTime emailedAt;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
