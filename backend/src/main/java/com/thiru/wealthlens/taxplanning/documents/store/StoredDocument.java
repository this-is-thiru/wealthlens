package com.thiru.wealthlens.taxplanning.documents.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * An uploaded document as stored (TS T7; W-131a, D-761). The same bytes are one document per profile, not per world: a key
 * across accounts would refuse a person a file they never stored and tell them someone else has (D-761 C2a). Nothing here says
 * what the form holds, so nothing here is encrypted; where the bytes are is W-131b's.
 */
@Entity
@Table(name = "tax_documents")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class StoredDocument implements CreatedWithId {

    @Id
    private String id;

    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "tax_year_start")
    private int taxYearStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type")
    private DocumentType documentType;

    @Column(name = "sha256")
    private String sha256;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "page_count")
    private int pageCount;

    /**
     * Whether the malware scan ran on these bytes before they were stored (W-131h C3a). False while the scan is switched off, so a
     * rescan once it is on knows which documents were never scanned; a document stored before W-131h reads false too.
     */
    @Column(name = "scanned")
    private boolean scanned;

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

    public static StoredDocument of(IngestedDocument document) {
        StoredDocument stored = new StoredDocument();
        stored.setId(document.documentId());
        stored.setProfileId(document.profileId());
        stored.setTaxYearStart(document.taxYear().startYear());
        stored.setDocumentType(document.documentType());
        stored.setSha256(document.sha256());
        stored.setUploadedAt(document.uploadedAt());
        stored.setPageCount(document.pageCount());
        stored.setNewlyCreated(true);
        return stored;
    }

    public IngestedDocument toRecord() {
        return new IngestedDocument(id, profileId, TaxYearId.of(taxYearStart), documentType, sha256, uploadedAt, pageCount);
    }
}
