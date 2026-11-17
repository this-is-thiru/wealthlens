package com.thiru.wealthlens.taxplanning.documents.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.protection.EncryptedField;
import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A person's confirmation of one attempt, as stored (TS T7; W-131a, D-761 C4a): one per attempt, so the attempt's id is its
 * key. The values the person accepted are encrypted; the paths they rejected name rows of a form, not what the rows hold.
 */
@Data
@NoArgsConstructor
@Document("tax_document_confirmations")
public class StoredConfirmation implements AuditableEntity, CreatedWithId {

    @MongoId
    private String id;

    @ValueConverter(SearchableIdentifier.class)
    @Field("confirmed_by")
    private String confirmedBy;

    @Field("confirmed_at")
    private LocalDateTime confirmedAt;

    @ValueConverter(EncryptedField.class)
    @Field("accepted_json")
    private String acceptedJson;

    @Field("rejected_fields")
    private List<String> rejectedFields = List.of();

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

    public static StoredConfirmation of(DocumentConfirmation confirmation) {
        StoredConfirmation stored = new StoredConfirmation();
        stored.setId(confirmation.attemptId());
        stored.setConfirmedBy(confirmation.confirmedBy());
        stored.setConfirmedAt(confirmation.confirmedAt());
        stored.setAcceptedJson(ReadingJson.writeValues(confirmation.acceptedFields()));
        stored.setRejectedFields(confirmation.rejectedFields().stream().sorted().toList());
        stored.setNewlyCreated(true);
        return stored;
    }

    public DocumentConfirmation toRecord() {
        return new DocumentConfirmation(id, confirmedBy, confirmedAt, ReadingJson.readValues(acceptedJson), new HashSet<>(rejectedFields));
    }
}
