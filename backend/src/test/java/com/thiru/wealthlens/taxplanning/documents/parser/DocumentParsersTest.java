package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentParsersTest {

    private static final DocumentContent CONTENT = new DocumentContent("abc".getBytes(StandardCharsets.US_ASCII));
    /** SHA-256("abc"), the FIPS 180-4 test vector. */
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    /** 10:30 in India is 05:00 UTC: IST is UTC+05:30. */
    private static final Clock IST = Clock.fixed(Instant.parse("2027-06-15T05:00:00Z"), ZoneId.of("Asia/Kolkata"));

    @Test
    void parse_whenItsFormHasAParser_recordsTheReadingAsAnAttemptTimedInUtc() {
        // Given
        DocumentParsers parsers = DocumentParsers.of(List.of(new StubParser(DocumentType.FORM_130, "form-130")), IST);

        // When
        ParseAttempt attempt = parsers.parse(document(DocumentType.FORM_130, ABC_SHA256), CONTENT);

        // Then
        assertThat(attempt.documentId()).isEqualTo("doc-1");
        assertThat(attempt.attemptId()).isNotBlank();
        assertThat(attempt.run().parserId()).isEqualTo("form-130");
        assertThat(attempt.run().parserVersion()).isEqualTo("1");
        assertThat(attempt.run().startedAt()).isEqualTo(LocalDateTime.of(2027, 6, 15, 5, 0));
        assertThat(attempt.run().finishedAt()).isEqualTo(LocalDateTime.of(2027, 6, 15, 5, 0));
        assertThat(attempt.outcome()).isEqualTo(ParseOutcome.SUCCESS);
        assertThat(attempt.extractedFields()).extracting(ExtractedField::fieldPath).containsExactly("partC.annexureI.rowA");
    }

    @Test
    void parse_whenTheContentIsNotTheDocuments_refuses() {
        // Given
        DocumentParsers parsers = DocumentParsers.of(List.of(new StubParser(DocumentType.FORM_130, "form-130")), IST);
        String otherDigest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        // When / Then
        assertThatThrownBy(() -> parsers.parse(document(DocumentType.FORM_130, otherDigest), CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The content is not this document's");
    }

    @Test
    void parse_whenNoParserReadsItsForm_refusesNamingIt() {
        // Given
        DocumentParsers parsers = DocumentParsers.of(List.of(new StubParser(DocumentType.FORM_130, "form-130")), IST);

        // When / Then
        assertThatThrownBy(() -> parsers.parse(document(DocumentType.FORM_122, ABC_SHA256), CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No parser reads FORM_122 yet");
    }

    @Test
    void of_whenTwoParsersReadOneForm_refusesNamingBoth() {
        // Given
        List<DocumentParser> two = List.of(new StubParser(DocumentType.FORM_16, "first"),
                new StubParser(DocumentType.FORM_16, "second"));

        // When / Then
        assertThatThrownBy(() -> DocumentParsers.of(two, IST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Two parsers read FORM_16: first and second");
    }

    private static IngestedDocument document(DocumentType type, String sha256) {
        return new IngestedDocument("doc-1", "profile-1", TaxYearId.of(2026), type, sha256,
                LocalDateTime.of(2027, 6, 15, 4, 59), 1);
    }

    /** Reads one regime flag, whatever the bytes. */
    private record StubParser(DocumentType reads, String id) implements DocumentParser {

        @Override
        public String version() {
            return "1";
        }

        @Override
        public DocumentReading read(DocumentContent content, TaxYearId year) {
            return new DocumentReading(ParseOutcome.SUCCESS, List.of(new ExtractedField("partC.annexureI.rowA",
                    Optional.of("No"), Optional.of(new ExtractedValue.FlagRead(false)), 1, Optional.empty(),
                    Optional.empty())), List.of(), List.of());
        }
    }
}
