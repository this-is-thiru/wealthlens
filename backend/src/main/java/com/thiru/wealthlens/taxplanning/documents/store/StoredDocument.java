package com.thiru.wealthlens.taxplanning.documents.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * An uploaded document as stored (TS T7; W-131a, D-761). The same bytes are one document per profile, not per world: a key
 * across accounts would refuse a person a file they never stored and tell them someone else has (D-761 C2a). Nothing here says
 * what the form holds, so nothing here is encrypted; where the bytes are is W-131b's.
 */
@Data
@NoArgsConstructor
@Document("tax_documents")
@CompoundIndexes({
    @CompoundIndex(name = "tax_document_bytes_idx", def = "{'profile_id': 1, 'sha256': 1}", unique = true),
    @CompoundIndex(name = "tax_document_year_idx", def = "{'profile_id': 1, 'tax_year': 1}")
})
public class StoredDocument implements AuditableEntity, CreatedWithId {

    @MongoId
    private String id;

    @Field("profile_id")
    private String profileId;

    @Field("tax_year")
    private int taxYearStart;

    @Field(name = "document_type", targetType = FieldType.STRING)
    private DocumentType documentType;

    @Field("sha256")
    private String sha256;

    @Field("uploaded_at")
    private LocalDateTime uploadedAt;

    @Field("page_count")
    private int pageCount;

    /**
     * Whether the malware scan ran on these bytes before they were stored (W-131h C3a). False while the scan is switched off, so a
     * rescan once it is on knows which documents were never scanned; a document stored before W-131h reads false too.
     */
    @Field("scanned")
    private boolean scanned;

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
