package com.thiru.wealthlens.taxplanning.documents.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.MongoTimeoutException;
import com.thiru.wealthlens.shared.exception.ServiceUnavailableException;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.shared.protection.PersonalDataKeys;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import com.thiru.wealthlens.testsupport.TestPdfs;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Period;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

@ExtendWith(MockitoExtension.class)
class DocumentBytesTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final FieldCipher CIPHER = new FieldCipher(new PersonalDataKeys(KEY, KEY));

    @Mock
    private GridFsTemplate bucket;

    private DocumentBytes bytes(long maxBytes, int maxPages) {
        return new DocumentBytes(bucket, CIPHER, new DocumentSettings(maxBytes, maxPages, Period.ofDays(1), java.time.Duration.ofSeconds(30)));
    }

    @Test
    void store_whenTheBytesAreNotAPdf_refusesAndWritesNothing() {
        // Given
        DocumentContent content = new DocumentContent("PK zip".getBytes(StandardCharsets.US_ASCII));

        // When / Then
        assertThatThrownBy(() -> bytes(1000, 50).store("document-1", content)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only a PDF can be uploaded");
        verifyNoInteractions(bucket);
    }

    @Test
    void store_whenOneByteOverTheLimit_refuses() {
        // Given — a limit one byte short of the file
        byte[] file = TestPdfs.pages(1);

        // When / Then
        assertThatThrownBy(() -> bytes(file.length - 1, 50).store("document-1", new DocumentContent(file)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A document is at most " + (file.length - 1) + " bytes");
        verifyNoInteractions(bucket);
    }

    @Test
    void store_whenExactlyAtBothLimits_storesItEncryptedUnderItsIdAndCountsItsPages() throws java.io.IOException {
        // Given — two pages against a limit of two, and the file's own length as the byte limit
        byte[] file = TestPdfs.pages(2);

        // When
        int pages = bytes(file.length, 2).store("document-1", new DocumentContent(file));

        // Then
        assertThat(pages).isEqualTo(2);
        org.mockito.ArgumentCaptor<InputStream> written = org.mockito.ArgumentCaptor.forClass(InputStream.class);
        verify(bucket).store(written.capture(), eq("document-1"));
        byte[] stored = written.getValue().readAllBytes();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF-");
        assertThat(CIPHER.decryptBytes(stored)).isEqualTo(file);
    }

    @Test
    void store_whenOnePageOverTheLimit_refuses() {
        // Given
        byte[] file = TestPdfs.pages(3);

        // When / Then
        assertThatThrownBy(() -> bytes(file.length, 2).store("document-1", new DocumentContent(file)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A document has at most 2 pages");
        verifyNoInteractions(bucket);
    }

    @Test
    void store_whenThePdfNeedsAPassword_refusesWithoutAskingForIt() {
        // Given
        byte[] file = TestPdfs.locked();

        // When / Then
        assertThatThrownBy(() -> bytes(file.length, 50).store("document-1", new DocumentContent(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The PDF is protected by a password: upload it without one (a password is never kept)");
        verifyNoInteractions(bucket);
    }

    @Test
    void store_whenTheStoreCannotBeReached_isUnavailable() {
        // Given — Spring's failure and the driver's own, either of which an unreachable server raises
        byte[] file = TestPdfs.pages(1);
        when(bucket.store(any(InputStream.class), eq("document-1")))
                .thenThrow(new DataAccessResourceFailureException("down"))
                .thenThrow(new MongoTimeoutException("down"));

        // When / Then
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> bytes(file.length, 50).store("document-1", new DocumentContent(file)))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessage("The document store cannot be reached; try again later");
        }
    }
}
