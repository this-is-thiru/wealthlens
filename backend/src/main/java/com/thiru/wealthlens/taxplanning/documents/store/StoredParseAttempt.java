package com.thiru.wealthlens.taxplanning.documents.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.shared.protection.EncryptedColumn;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * One try at reading a document, as stored (TS T7; W-131a, D-761 C4a). What was read — the fields, the text left unread and the
 * parser's notes — is encrypted; which parser ran, when and how it ended stay plain, since a rerun is found by them
 * ({@code DR 19} E8).
 */
@Entity
@Table(name = "tax_parse_attempts")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class StoredParseAttempt implements CreatedWithId {

    @Id
    private String id;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "parser_id")
    private String parserId;

    @Column(name = "parser_version")
    private String parserVersion;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private ParseOutcome outcome;

    @Convert(converter = EncryptedColumn.class)
    @Column(name = "fields_json")
    private String fieldsJson;

    @Convert(converter = EncryptedColumn.class)
    @Column(name = "unmapped_text_json")
    private String unmappedTextJson;

    @Convert(converter = EncryptedColumn.class)
    @Column(name = "parser_notes_json")
    private String parserNotesJson;

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

    public static StoredParseAttempt of(ParseAttempt attempt) {
        StoredParseAttempt stored = new StoredParseAttempt();
        stored.setId(attempt.attemptId());
        stored.setDocumentId(attempt.documentId());
        stored.setParserId(attempt.run().parserId());
        stored.setParserVersion(attempt.run().parserVersion());
        stored.setStartedAt(attempt.run().startedAt());
        stored.setFinishedAt(attempt.run().finishedAt());
        stored.setOutcome(attempt.outcome());
        stored.setFieldsJson(ReadingJson.writeFields(attempt.extractedFields()));
        stored.setUnmappedTextJson(ReadingJson.writeTexts(attempt.unmappedText()));
        stored.setParserNotesJson(ReadingJson.writeTexts(attempt.parserNotes()));
        stored.setNewlyCreated(true);
        return stored;
    }

    public ParseAttempt toRecord() {
        return new ParseAttempt(id, documentId, new ParserRun(parserId, parserVersion, startedAt, finishedAt), outcome,
                ReadingJson.readFields(fieldsJson), ReadingJson.readTexts(unmappedTextJson), ReadingJson.readTexts(parserNotesJson));
    }
}
