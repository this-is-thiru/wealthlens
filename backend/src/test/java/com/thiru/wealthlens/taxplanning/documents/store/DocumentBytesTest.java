package com.thiru.wealthlens.taxplanning.documents.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.shared.exception.ServiceUnavailableException;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import com.thiru.wealthlens.taxplanning.documents.parser.IsolatedPdfRead;
import com.thiru.wealthlens.testsupport.TestPdfs;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class DocumentBytesTest {

    private static final DocumentSettings.Isolation ISOLATION = new DocumentSettings.Isolation(256, 30, Duration.ofSeconds(10), 16_777_216);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-05T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TaxDocumentBytesRepository repository;

    @Mock
    private MalwareScan scan;

    private DocumentBytes bytes(long maxBytes, int maxPages) {
        return new DocumentBytes(repository, new DocumentSettings(maxBytes, maxPages, Period.ofDays(1), Duration.ofSeconds(30), true,
                new DocumentSettings.Scan(true, "localhost", 3310, Duration.ofSeconds(60)), ISOLATION), scan, CLOCK,
                IsolatedPdfRead.of(ISOLATION, Duration.ofSeconds(30)));
    }

    @Test
    void store_whenTheBytesAreNotAPdf_refusesAndWritesNothing() {
        // Given
        DocumentContent content = new DocumentContent("PK zip".getBytes(StandardCharsets.US_ASCII));

        // When / Then
        assertThatThrownBy(() -> bytes(1000, 50).store("document-1", content)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only a PDF can be uploaded");
        verifyNoInteractions(repository);
    }

    @Test
    void store_whenTheScanRefuses_refusesBeforeCheckingItIsAPdfAndWritesNothing() {
        // Given — bytes that are not a PDF, so a scan placed after the PDF checks would never be reached
        byte[] file = "PK zip".getBytes(StandardCharsets.US_ASCII);
        doThrow(new IllegalArgumentException("This file failed the malware scan and was not stored")).when(scan).requireClean(file);

        // When / Then
        assertThatThrownBy(() -> bytes(1000, 50).store("document-1", new DocumentContent(file))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("This file failed the malware scan and was not stored");
        verifyNoInteractions(repository);
    }

    @Test
    void store_whenOneByteOverTheLimit_refuses() {
        // Given — a limit one byte short of the file
        byte[] file = TestPdfs.pages(1);

        // When / Then
        assertThatThrownBy(() -> bytes(file.length - 1, 50).store("document-1", new DocumentContent(file)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A document is at most " + (file.length - 1) + " bytes");
        verifyNoInteractions(repository);
    }

    @Test
    void store_whenExactlyAtBothLimits_storesItUnderItsIdAndCountsItsPages() throws java.io.IOException {
        // Given — two pages against a limit of two, and the file's own length as the byte limit
        byte[] file = TestPdfs.pages(2);

        // When
        int pages = bytes(file.length, 2).store("document-1", new DocumentContent(file));

        // Then
        assertThat(pages).isEqualTo(2);
        verify(scan).requireClean(file);
        ArgumentCaptor<TaxDocumentBytes> written = ArgumentCaptor.forClass(TaxDocumentBytes.class);
        verify(repository).save(written.capture());
        TaxDocumentBytes stored = written.getValue();
        assertThat(stored.getDocumentId()).isEqualTo("document-1");
        // plain since W-428a (D-1036)
        assertThat(stored.getBytes()).isEqualTo(file);
    }

    @Test
    void store_whenOnePageOverTheLimit_refuses() {
        // Given
        byte[] file = TestPdfs.pages(3);

        // When / Then
        assertThatThrownBy(() -> bytes(file.length, 2).store("document-1", new DocumentContent(file)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A document has at most 2 pages");
        verifyNoInteractions(repository);
    }

    @Test
    void store_whenThePdfNeedsAPassword_refusesWithoutAskingForIt() {
        // Given
        byte[] file = TestPdfs.locked();

        // When / Then
        assertThatThrownBy(() -> bytes(file.length, 50).store("document-1", new DocumentContent(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The PDF is protected by a password: upload it without one (a password is never kept)");
        verifyNoInteractions(repository);
    }

    @Test
    void store_whenTheStoreCannotBeReached_isUnavailable() {
        // Given — Spring's own translation of a connection failure, now that this is a normal repository call
        byte[] file = TestPdfs.pages(1);
        when(repository.save(any(TaxDocumentBytes.class))).thenThrow(new DataAccessResourceFailureException("down"));

        // When / Then
        assertThatThrownBy(() -> bytes(file.length, 50).store("document-1", new DocumentContent(file)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("The document store cannot be reached; try again later");
    }
}
