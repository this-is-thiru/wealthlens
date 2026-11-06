package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.protection.EncryptedField;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One version of a year's entries ({@code DR 11} F2). {@code inputsJson} is {@code EnteredFacts} as JSON text, one model with the
 * engine's rather than a second to keep in step (D-441 C1a); text, so no amount is reshaped by storage.
 */
@Data
@NoArgsConstructor
@Document("tax_filing_drafts")
@CompoundIndex(name = "tax_draft_workspace_idx", def = "{'workspace_id': 1, 'status': 1}")
public class FilingDraft implements AuditableEntity {

    @MongoId
    private String id;

    @Field("workspace_id")
    private String workspaceId;

    @Field("profile_id")
    private String profileId;

    @Field("tax_year")
    private int taxYearStart;

    @Field("label")
    private String label;

    @Field(name = "status", targetType = FieldType.STRING)
    private DraftStatus status;

    @Field("current_revision")
    private int currentRevision;

    @ValueConverter(EncryptedField.class)
    @Field("inputs_json")
    private String inputsJson;

    @Field("regime_election")
    private String regimeElection;

    /** When it was acted on, in UTC: a filed draft is kept by this, whatever its status later ({@code DR 14} O5; W-137). */
    @Field("acted_on_at")
    private LocalDateTime actedOnAt;

    /** When it was last archived, in UTC; cleared on restore. What an archived draft's retention runs from (W-137). */
    @Field("archived_at")
    private LocalDateTime archivedAt;

    /** Set by the staleness sweep when the law moved under this draft's last computation; cleared by computing again (W-138). */
    @Field("staleness")
    private DraftStaleness staleness;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();

    /** Moves to {@code next}, recording when it was acted on or archived; a draft leaving {@code ARCHIVED} is no longer archived. */
    public void moveTo(DraftStatus next, LocalDateTime atUtc) {
        status = next;
        if (next == DraftStatus.ACTED_ON) {
            actedOnAt = atUtc;
        }
        archivedAt = next == DraftStatus.ARCHIVED ? atUtc : null;
    }
}
