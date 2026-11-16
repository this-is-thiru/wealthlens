package com.thiru.wealthlens.taxplanning.documents.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IngestedDocumentTest {

    /** The SHA-256 of the empty input (FIPS 180-4 test vector): 64 lowercase hex characters. */
    private static final String SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final TaxYearId YEAR = TaxYearId.of(2026);
    private static final LocalDateTime UPLOADED = LocalDateTime.of(2027, 6, 15, 10, 30);

    @Test
    void new_whenWellFormed_keepsEveryPart() {
        // When
        IngestedDocument document = new IngestedDocument("doc-1", "profile-1", YEAR, DocumentType.FORM_130, SHA256,
                UPLOADED, 4);

        // Then
        assertThat(document.sha256()).isEqualTo(SHA256);
        assertThat(document.documentType()).isEqualTo(DocumentType.FORM_130);
        assertThat(document.pageCount()).isEqualTo(4);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85",
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855a",
        "g3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"})
    void new_whenTheDigestIsNotSha256Hex_refuses(String sha256) {
        // When / Then
        assertThatThrownBy(() -> new IngestedDocument("doc-1", "profile-1", YEAR, DocumentType.FORM_16, sha256, UPLOADED, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A document's SHA-256 is 64 lowercase hexadecimal characters");
    }

    @Test
    void new_whenItHasNoPage_refuses() {
        // When / Then
        assertThatThrownBy(() -> new IngestedDocument("doc-1", "profile-1", YEAR, DocumentType.FORM_16, SHA256, UPLOADED, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A document has at least one page");
    }

    @Test
    void new_whenItBelongsToNoProfile_refuses() {
        // When / Then
        assertThatThrownBy(() -> new IngestedDocument("doc-1", " ", YEAR, DocumentType.FORM_16, SHA256, UPLOADED, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A document belongs to a profile");
    }

    @Test
    void new_whenItHasNoType_refuses() {
        // When / Then
        assertThatThrownBy(() -> new IngestedDocument("doc-1", "profile-1", YEAR, null, SHA256, UPLOADED, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A document needs its type");
    }
}
