package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.protection.EncryptedField;
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

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
