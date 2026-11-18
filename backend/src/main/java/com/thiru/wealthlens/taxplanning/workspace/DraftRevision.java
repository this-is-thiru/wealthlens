package com.thiru.wealthlens.taxplanning.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.protection.EncryptedField;
import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One accepted save: append-only, and no method here or in its service updates or deletes one ({@code DR 11} F2). Revision nil holds
 * the full state and later ones a patch, so any state can be replayed; the unique key is what stops two saves of one revision.
 */
@Data
@NoArgsConstructor
@Document("tax_draft_revisions")
@CompoundIndex(name = "tax_revision_number_idx", def = "{'draft_id': 1, 'revision_number': 1}", unique = true)
public class DraftRevision implements AuditableEntity, CreatedWithId {

    @MongoId
    private String id;

    @Field("draft_id")
    private String draftId;

    @Field("revision_number")
    private int revisionNumber;

    @ValueConverter(EncryptedField.class)
    @Field("full_state_json")
    private String fullStateJson;

    @ValueConverter(EncryptedField.class)
    @Field("change_set_json")
    private String changeSetJson;

    @ValueConverter(SearchableIdentifier.class)
    @Field("changed_by")
    private String changedBy;

    /** Whether this edit took a {@code READY} draft back to {@code DRAFT}. */
    @Field("reopened")
    private boolean reopened;

    /** Where the change came from, if a salary workbook (W-133b); none for every other save, and for every save before W-133b. */
    @Field("imported_from")
    private WorkbookImport importedFrom;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();

    /** Whether this instance is not yet stored (W-329b): never stored, never read back. */
    @Transient
    @JsonIgnore
    private boolean newlyCreated;

    @Override
    public boolean isNew() {
        return newlyCreated || id == null;
    }
}
