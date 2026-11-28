package com.thiru.wealthlens.taxplanning.documents.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A document's bytes as uploaded ({@code DR 14} O6; W-131b; stored plain since W-428c, D-1040), one row per
 * document (W-337f, D-951): the document's own id is the primary key,
 * caller-assigned, not generated here.
 */
@Entity
@Table(name = "tax_document_bytes")
@Data
@NoArgsConstructor
public class TaxDocumentBytes {

    @Id
    @Column(name = "document_id")
    private String documentId;

    @Column(name = "bytes")
    private byte[] bytes;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;
}
