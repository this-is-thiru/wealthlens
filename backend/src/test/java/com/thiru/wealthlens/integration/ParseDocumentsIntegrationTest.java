package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentParser;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentReading;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentBytes;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttemptRepository;
import com.thiru.wealthlens.taxplanning.jobs.ParseDocuments;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.testsupport.TestPdfs;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

/**
 * W-131c (D-764): every stored document read once per parser version, within the budget, never for a held profile. Form No. 122
 * has no parser of its own, so a test parser reads it and is made slow or broken per test; Form No. 130 is read by the real one.
 */
@TestPropertySource(properties = "app.tax.documents.parse-budget=PT0.2S")
class ParseDocumentsIntegrationTest extends AbstractIntegrationTest {

    /** How the test parser behaves in the running test. */
    enum Behaviour { READS, SLOW, BREAKS }

    static volatile Behaviour behaviour = Behaviour.READS;

    @TestConfiguration
    static class TestParser {

        @Bean
        DocumentParser form122Parser() {
            return new DocumentParser() {
                @Override
                public DocumentType reads() {
                    return DocumentType.FORM_122;
                }

                @Override
                public String id() {
                    return "TEST_FORM_122";
                }

                @Override
                public String version() {
                    return "2";
                }

                @Override
                public DocumentReading read(DocumentContent content, TaxYearId year) {
                    if (behaviour == Behaviour.SLOW) {
                        try {
                            Thread.sleep(5_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (behaviour == Behaviour.BREAKS) {
                        throw new IllegalStateException("a hostile file");
                    }
                    return new DocumentReading(ParseOutcome.SUCCESS, List.of(), List.of(), List.of());
                }
            };
        }
    }

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private ParseDocuments parseDocuments;

    @Autowired
    private DocumentBytes documentBytes;

    @Autowired
    private StoredParseAttemptRepository attempts;

    @BeforeEach
    void theParserReads() {
        behaviour = Behaviour.READS;
    }

    /** A document of {@code type} under {@code profileId}, its bytes stored. */
    private void uploaded(String id, String profileId, DocumentType type) {
        byte[] pdf = TestPdfs.pages(1);
        mongoTemplate.save(StoredDocument.of(new IngestedDocument(id, profileId, TaxYearId.of(2026), type, new DocumentContent(pdf).sha256(),
                LocalDateTime.parse("2027-06-01T09:00:00"), 1)));
        documentBytes.store(id, new DocumentContent(pdf));
    }

    private void earlierAttempt(String documentId, ParseOutcome outcome) {
        attempts.save(StoredParseAttempt.of(new ParseAttempt("earlier-" + documentId, documentId, new ParserRun("TEST_FORM_122", "1",
                LocalDateTime.parse("2027-06-01T09:01:00"), LocalDateTime.parse("2027-06-01T09:02:00")), outcome, List.of(), List.of(),
                outcome == ParseOutcome.FAILED ? List.of("an older parser could not read it") : List.of())));
    }

    private List<ParseAttempt> attemptsAt(String documentId) {
        return attempts.findByDocumentIdIn(List.of(documentId)).stream().map(StoredParseAttempt::toRecord).toList();
    }

    @Test
    void run_whenADocumentIsStored_readsItOnceWithTheCurrentParser() {
        // Given — one read by the test parser, one by the real Form No. 130 parser
        uploaded("document-122", "profile-1", DocumentType.FORM_122);
        uploaded("document-130", "profile-1", DocumentType.FORM_130);

        // When
        runner.run(parseDocuments);
        runner.run(parseDocuments);

        // Then — one attempt each, whatever its outcome
        assertThat(attemptsAt("document-122")).singleElement().satisfies(attempt -> {
            assertThat(attempt.outcome()).isEqualTo(ParseOutcome.SUCCESS);
            assertThat(attempt.run().parserVersion()).isEqualTo("2");
        });
        assertThat(attemptsAt("document-130")).singleElement().satisfies(attempt -> assertThat(attempt.run().parserId()).isNotEqualTo("TEST_FORM_122"));
    }

    @Test
    void run_whenTheProfileIsHeldForErasure_leavesItsDocumentUnread() {
        // Given
        TaxpayerProfile held = new TaxpayerProfile();
        held.setId("held-profile");
        held.setErasureRequestedAt(LocalDateTime.parse("2027-05-01T10:00:00"));
        mongoTemplate.save(held);
        uploaded("document-held", "held-profile", DocumentType.FORM_122);

        // When
        runner.run(parseDocuments);

        // Then
        assertThat(attemptsAt("document-held")).isEmpty();
    }

    @Test
    void run_whenTheParserOverrunsItsBudget_recordsAFailedAttemptSayingSo() {
        // Given — the budget is PT0.2S here; the parser takes five seconds
        behaviour = Behaviour.SLOW;
        uploaded("document-slow", "profile-1", DocumentType.FORM_122);

        // When
        runner.run(parseDocuments);

        // Then
        assertThat(attemptsAt("document-slow")).singleElement().satisfies(attempt -> {
            assertThat(attempt.outcome()).isEqualTo(ParseOutcome.FAILED);
            assertThat(attempt.parserNotes()).containsExactly("The parser took longer than PT0.2S");
        });
    }

    @Test
    void run_whenTheParserThrows_recordsAFailedAttemptAndKeepsTheDocument() {
        // Given
        behaviour = Behaviour.BREAKS;
        uploaded("document-hostile", "profile-1", DocumentType.FORM_122);

        // When
        runner.run(parseDocuments);

        // Then
        assertThat(attemptsAt("document-hostile")).singleElement().satisfies(attempt -> {
            assertThat(attempt.outcome()).isEqualTo(ParseOutcome.FAILED);
            assertThat(attempt.parserNotes()).containsExactly("The parser stopped: IllegalStateException");
        });
        assertThat(mongoTemplate.findById("document-hostile", StoredDocument.class)).isNotNull();
    }

    @Test
    void run_whenTheStoredBytesAreNotTheDocuments_recordsAFailedAttemptOnce() {
        // Given — the document names other bytes than those stored under it
        mongoTemplate.save(StoredDocument.of(new IngestedDocument("document-swapped", "profile-1", TaxYearId.of(2026), DocumentType.FORM_122,
                "c".repeat(64), LocalDateTime.parse("2027-06-01T09:00:00"), 1)));
        documentBytes.store("document-swapped", new DocumentContent(TestPdfs.pages(1)));

        // When
        runner.run(parseDocuments);
        runner.run(parseDocuments);

        // Then
        assertThat(attemptsAt("document-swapped")).singleElement().satisfies(attempt -> {
            assertThat(attempt.outcome()).isEqualTo(ParseOutcome.FAILED);
            assertThat(attempt.parserNotes()).containsExactly("The stored bytes are not this document's");
        });
    }

    @Test
    void run_whenAnOlderVersionFailed_readsItAgainButNotOneAnOlderVersionRead() {
        // Given — version 1 failed on one and read the other; the parser is now version 2
        uploaded("document-failed-before", "profile-1", DocumentType.FORM_122);
        earlierAttempt("document-failed-before", ParseOutcome.FAILED);
        uploaded("document-read-before", "profile-1", DocumentType.FORM_122);
        earlierAttempt("document-read-before", ParseOutcome.PARTIAL);

        // When
        runner.run(parseDocuments);

        // Then
        assertThat(attemptsAt("document-failed-before")).extracting(attempt -> attempt.run().parserVersion())
                .containsExactlyInAnyOrder("1", "2");
        assertThat(attemptsAt("document-read-before")).extracting(attempt -> attempt.run().parserVersion()).containsExactly("1");
        assertThat(Set.copyOf(attemptsAt("document-read-before"))).hasSize(1);
    }
}
