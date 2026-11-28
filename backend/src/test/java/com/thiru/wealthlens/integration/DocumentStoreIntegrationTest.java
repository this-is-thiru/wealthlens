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
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentBytes;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentRemoval;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmation;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmationRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttemptRepository;
import com.thiru.wealthlens.taxplanning.documents.store.TaxDocumentBytesRepository;
import com.thiru.wealthlens.testsupport.TestPdfs;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** W-131a (D-761): the stored form of a document, its attempts and confirmations — encrypted where it says what the form holds. */
class DocumentStoreIntegrationTest extends AbstractIntegrationTest {

    private static final String BYTES = "a".repeat(64);

    @Autowired
    private StoredDocumentRepository documents;

    @Autowired
    private StoredParseAttemptRepository attempts;

    @Autowired
    private StoredConfirmationRepository confirmations;

    @Autowired
    private DocumentBytes documentBytes;

    @Autowired
    private DocumentRemoval documentRemoval;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TaxDocumentBytesRepository documentBytesRepository;

    private static IngestedDocument document(String id, String profileId) {
        return new IngestedDocument(id, profileId, TaxYearId.of(2026), DocumentType.FORM_130, BYTES,
                LocalDateTime.of(2027, 6, 1, 9, 0), 3);
    }

    private Map<String, Object> raw(String table, String id) {
        return new JdbcTemplate(postgresDataSource).queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
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
        Map<String, Object> storedAttempt = raw("tax_parse_attempts", "attempt-1");
        String attemptAsText = storedAttempt.values().stream().map(String::valueOf).collect(Collectors.joining("|"));
        assertThat(attemptAsText).doesNotContain("1,00,000", "100000", "2026-04-01", "Signature", "row 8 not found");
        assertThat(storedAttempt.get("parser_version")).isEqualTo("3");
        assertThat(storedAttempt.get("outcome")).isEqualTo("PARTIAL");
        Map<String, Object> storedConfirmation = raw("tax_document_confirmations", "attempt-1");
        String confirmationAsText = storedConfirmation.values().stream().map(String::valueOf).collect(Collectors.joining("|"));
        assertThat(confirmationAsText).doesNotContain("950000", "owner@example.com");
        assertThat(storedConfirmation.get("confirmed_by")).isEqualTo(storedIdentifier("owner@example.com"));
    }

    @Test
    void save_whenOneProfileStoresTheSameBytesTwice_refusesTheSecond() {
        // Given
        documents.save(StoredDocument.of(document("document-1", "profile-1")));

        // When / Then
        assertThatThrownBy(() -> documents.save(StoredDocument.of(document("document-2", "profile-1"))))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    @Test
    void store_whenAPdfIsStored_readsBackEqualAndKeepsNoPlainByteInPostgres() {
        // Given
        byte[] pdf = TestPdfs.pages(2);

        // When
        int pages = documentBytes.store("document-1", new DocumentContent(pdf));

        // Then
        assertThat(pages).isEqualTo(2);
        assertThat(documentBytes.read("document-1").bytes()).isEqualTo(pdf);
        byte[] stored = documentBytesRepository.findById("document-1").orElseThrow().getBytes();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF-");
    }

    @Test
    void removeUnder_whenTheTransactionRollsBack_keepsTheDocumentAndItsBytes() {
        // Given
        documents.save(StoredDocument.of(document("document-1", "profile-1")));
        documentBytes.store("document-1", new DocumentContent(TestPdfs.pages(1)));

        // When — the erasure's transaction fails after the removal ran
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            documentRemoval.removeUnder("profile-1");
            status.setRollbackOnly();
        });

        // Then
        assertThat(documents.findById("document-1")).isPresent();
        assertThat(documentBytes.read("document-1").bytes()).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void removeUnder_whenTheTransactionCommits_removesTheBytesToo() {
        // Given
        documents.save(StoredDocument.of(document("document-1", "profile-1")));
        documentBytes.store("document-1", new DocumentContent(TestPdfs.pages(1)));

        // When
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> documentRemoval.removeUnder("profile-1"));

        // Then
        assertThat(documents.findById("document-1")).isEmpty();
        assertThat(documentBytesRepository.count()).isZero();
    }
}
