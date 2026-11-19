package com.thiru.wealthlens.taxplanning.documents.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.protection.EncryptedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One try at reading a document, as stored (TS T7; W-131a, D-761 C4a). What was read — the fields, the text left unread and the
 * parser's notes — is encrypted; which parser ran, when and how it ended stay plain, since a rerun is found by them
 * ({@code DR 19} E8).
 */
@Data
@NoArgsConstructor
@Document("tax_parse_attempts")
@CompoundIndex(name = "tax_parse_attempt_document_idx", def = "{'document_id': 1, 'started_at': -1}")
public class StoredParseAttempt implements AuditableEntity, CreatedWithId {

    @MongoId
    private String id;

    @Field("document_id")
    private String documentId;

    @Field("parser_id")
    private String parserId;

    @Field("parser_version")
    private String parserVersion;

    @Field("started_at")
    private LocalDateTime startedAt;

    @Field("finished_at")
    private LocalDateTime finishedAt;

    @Field(name = "outcome", targetType = FieldType.STRING)
    private ParseOutcome outcome;

    @ValueConverter(EncryptedField.class)
    @Field("fields_json")
    private String fieldsJson;

    @ValueConverter(EncryptedField.class)
    @Field("unmapped_text_json")
    private String unmappedTextJson;

    @ValueConverter(EncryptedField.class)
    @Field("parser_notes_json")
    private String parserNotesJson;

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
