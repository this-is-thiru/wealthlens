package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/** One profile's one tax year, holding its drafts (T7, keyed by profile: D-046 B2 over {@code DR 11} F8). */
@Data
@NoArgsConstructor
@Document("tax_year_workspaces")
@CompoundIndex(name = "tax_workspace_year_idx", def = "{'profile_id': 1, 'tax_year': 1}", unique = true)
public class TaxYearWorkspace implements AuditableEntity {

    @MongoId
    private String id;

    @Field("profile_id")
    private String profileId;

    @Field("tax_year")
    private int taxYearStart;

    @Field("primary_draft_id")
    private String primaryDraftId;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
