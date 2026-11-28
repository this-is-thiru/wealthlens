package com.thiru.wealthlens.taxplanning.documents.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A person's confirmation of one attempt, as stored (TS T7; W-131a, D-761 C4a): one per attempt, so the attempt's id is its
 * key: the values the person accepted, and the paths they rejected (rows of a form, not what the rows hold).
 */
@Entity
@Table(name = "tax_document_confirmations")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class StoredConfirmation implements CreatedWithId {

    @Id
    private String id;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "accepted_json")
    private String acceptedJson;

    /** Child table from the W-337b baseline (V5), not a JSON column: {@code tax_document_confirmation_rejected_fields}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "tax_document_confirmation_rejected_fields", joinColumns = @JoinColumn(name = "confirmation_id"))
    @Column(name = "rejected_field")
    private List<String> rejectedFields = new ArrayList<>();

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

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
