package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmation;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmationRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttemptRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

/** W-131a (D-761): the stored form of a document, its attempts and confirmations — encrypted where it says what the form holds. */
class DocumentStoreIntegrationTest extends AbstractIntegrationTest {

    private static final String BYTES = "a".repeat(64);

    @Autowired
    private StoredDocumentRepository documents;

    @Autowired
    private StoredParseAttemptRepository attempts;

    @Autowired
    private StoredConfirmationRepository confirmations;

    private static IngestedDocument document(String id, String profileId) {
        return new IngestedDocument(id, profileId, TaxYearId.of(2026), DocumentType.FORM_130, BYTES,
                LocalDateTime.of(2027, 6, 1, 9, 0), 3);
    }

    private Document raw(String collection, String id) {
        return mongoTemplate.getCollection(collection).find(new Document("_id", id)).first();
    }

    @Test
    void save_whenADocumentItsAttemptAndConfirmationAreStored_readsThemBackEqualAndKeepsNoReadValuePlain() {
        // Given — row 12 read as "1,00,000" (₹1,00,000), confirmed as ₹9,50,000; a period; words the parser could not place
        IngestedDocument document = document("document-1", "profile-1");
        ParseAttempt attempt = new ParseAttempt("attempt-1", "document-1", new ParserRun("FORM_130", "3",
                LocalDateTime.of(2027, 6, 1, 9, 1), LocalDateTime.of(2027, 6, 1, 9, 2)), ParseOutcome.PARTIAL,
                List.of(new ExtractedField("c.row12", Optional.of("1,00,000"), Optional.of(new ExtractedValue.MoneyRead(Money.of("100000"))),
                        0.9, Optional.empty(), Optional.empty()),
                        new ExtractedField("a.period", Optional.of("01-04-2026 to 31-03-2027"), Optional.of(new ExtractedValue.PeriodRead(
                                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31))), 1, Optional.empty(), Optional.empty())),
                List.of("Signature of the person responsible"), List.of("row 8 not found"));
        DocumentConfirmation confirmation = new DocumentConfirmation("attempt-1", "owner@example.com", LocalDateTime.of(2027, 6, 1, 9, 5),
                Map.of("c.row12", new ExtractedValue.MoneyRead(Money.of("950000"))), Set.of("a.period"));

        // When
        documents.save(StoredDocument.of(document));
        attempts.save(StoredParseAttempt.of(attempt));
        confirmations.save(StoredConfirmation.of(confirmation));

        // Then — read back as the records
        assertThat(documents.findById("document-1").orElseThrow().toRecord()).isEqualTo(document);
        assertThat(attempts.findById("attempt-1").orElseThrow().toRecord()).isEqualTo(attempt);
        assertThat(confirmations.findById("attempt-1").orElseThrow().toRecord()).isEqualTo(confirmation);
        // Then — what the form says, and who confirmed it, is not stored as plain text
        String storedAttempt = raw("tax_parse_attempts", "attempt-1").toJson();
        assertThat(storedAttempt).doesNotContain("1,00,000", "100000", "2026-04-01", "Signature", "row 8 not found")
                .contains("\"parser_version\": \"3\"", "\"outcome\": \"PARTIAL\"");
        Document storedConfirmation = raw("tax_document_confirmations", "attempt-1");
        assertThat(storedConfirmation.toJson()).doesNotContain("950000", "owner@example.com");
        assertThat(storedConfirmation.getString("confirmed_by")).isEqualTo(storedIdentifier("owner@example.com"));
    }

    @Test
    void save_whenOneProfileStoresTheSameBytesTwice_refusesTheSecond() {
        // Given
        documents.save(StoredDocument.of(document("document-1", "profile-1")));

        // When / Then
        assertThatThrownBy(() -> documents.save(StoredDocument.of(document("document-2", "profile-1"))))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void save_whenAnotherProfileStoresTheSameBytes_keepsBoth() {
        // Given — the unique key is the profile's, not the world's (D-761 C2a)
        documents.save(StoredDocument.of(document("document-1", "profile-1")));

        // When
        documents.save(StoredDocument.of(document("document-2", "profile-2")));

        // Then
        assertThat(documents.count()).isEqualTo(2);
    }
}
