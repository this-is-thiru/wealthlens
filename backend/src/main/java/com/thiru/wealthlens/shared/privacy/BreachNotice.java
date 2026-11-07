package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A personal data breach as the owner reports it (Digital Personal Data Protection Act, 2023 s.8(6); Rules r.7; W-237c):
 * what each affected user is told (r.7(1)(a) – (d)), who they are, and when each step towards the Board was taken. Only
 * facts with their times are stored; whether a step is late is read from them.
 */
@Data
@NoArgsConstructor
@Document("privacy_breach_notices")
public class BreachNotice implements AuditableEntity {

    @MongoId
    private String id;

    @Field("description")
    private String description;

    @Field("consequences")
    private String consequences;

    @Field("mitigation")
    private String mitigation;

    @Field("safety_measures")
    private String safetyMeasures;

    /** UTC, as the owner states it. */
    @Field("became_aware_at")
    private LocalDateTime becameAwareAt;

    @Field("scope")
    private BreachScope scope;

    /** The emails the notice was posted to, resolved when it was posted. */
    @Field("affected")
    private List<String> affected = new ArrayList<>();

    /** UTC. */
    @Field("posted_at")
    private LocalDateTime postedAt;

    /** UTC; awareness plus the configured hours, or the later time the Board allowed. */
    @Field("board_report_due_at")
    private LocalDateTime boardReportDueAt;

    /** The Board's reference for a longer period it allowed, absent unless one was. */
    @Field("board_extension_reference")
    private String boardExtensionReference;

    /** UTC; absent until recorded. */
    @Field("board_first_intimated_at")
    private LocalDateTime boardFirstIntimatedAt;

    /** UTC; absent until recorded. */
    @Field("board_report_sent_at")
    private LocalDateTime boardReportSentAt;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
