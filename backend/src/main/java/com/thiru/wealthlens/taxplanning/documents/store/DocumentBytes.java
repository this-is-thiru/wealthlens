package com.thiru.wealthlens.taxplanning.documents.store;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.itextpdf.text.exceptions.BadPasswordException;
import com.itextpdf.text.pdf.PdfReader;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.thiru.wealthlens.shared.exception.ServiceUnavailableException;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/**
 * A document's bytes, kept in their own GridFS bucket under the document's id, sealed with the personal-data key, so nothing
 * stored is readable without the application ({@code DR 14} O6; W-131b, D-762). What is refused is refused before anything is
 * written: not a PDF, too large, locked by a password, or too long. A store that cannot be reached is a 503 (O9).
 *
 * @param bucket the {@code tax_document_bytes} bucket
 */
public record DocumentBytes(GridFsTemplate bucket, FieldCipher cipher, DocumentSettings settings) {

    private static final byte[] PDF_START = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /** Checks the bytes against O6's limits, then stores them sealed; returns the number of pages. */
    public int store(String documentId, DocumentContent content) {
        byte[] plain = content.bytes();
        if (plain.length > settings.maxBytes()) {
            throw new IllegalArgumentException("A document is at most " + settings.maxBytes() + " bytes");
        }
        if (plain.length < PDF_START.length || !Arrays.equals(plain, 0, PDF_START.length, PDF_START, 0, PDF_START.length)) {
            throw new IllegalArgumentException("Only a PDF can be uploaded");
        }
        int pages = pagesOf(plain);
        reachable(() -> bucket.store(new ByteArrayInputStream(cipher.encryptBytes(plain)), documentId));
        return pages;
    }

    /**
     * @throws IllegalStateException if the document has no stored bytes, or they cannot be read
     */
    public DocumentContent read(String documentId) {
        GridFSFile file = reachable(() -> bucket.findOne(query(where("filename").is(documentId))));
        if (file == null) {
            throw new IllegalStateException("Document " + documentId + " has no stored bytes");
        }
        try {
            return new DocumentContent(cipher.decryptBytes(reachable(() -> bucket.getResource(file)).getInputStream().readAllBytes()));
        } catch (IOException e) {
            throw new IllegalStateException("Document " + documentId + "'s stored bytes could not be read", e);
        }
    }

    public void remove(Collection<String> documentIds) {
        if (!documentIds.isEmpty()) {
            reachable(() -> {
                bucket.delete(query(where("filename").in(documentIds)));
                return documentIds.size();
            });
        }
    }

    /** The document ids of bytes stored before {@code instant}, whether or not a document still names them. */
    public List<String> storedBefore(Instant instant) {
        List<String> names = new ArrayList<>();
        reachable(() -> bucket.find(query(where("uploadDate").lt(Date.from(instant))))).forEach(file -> names.add(file.getFilename()));
        return names;
    }

    private int pagesOf(byte[] plain) {
        PdfReader reader;
        try {
            reader = new PdfReader(plain);
        } catch (BadPasswordException e) {
            throw new IllegalArgumentException("The PDF is protected by a password: upload it without one (a password is never kept)", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("The PDF cannot be opened: " + e.getMessage(), e);
        }
        try {
            int pages = reader.getNumberOfPages();
            if (pages < 1) {
                throw new IllegalArgumentException("The PDF has no pages");
            }
            if (pages > settings.maxPages()) {
                throw new IllegalArgumentException("A document has at most " + settings.maxPages() + " pages");
            }
            return pages;
        } finally {
            reader.close();
        }
    }

    /** Spring's failure to reach Mongo, and the driver's own: GridFS calls the driver without Spring's translation. */
    private static <T> T reachable(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessResourceFailureException | MongoTimeoutException | MongoSocketException e) {
            throw new ServiceUnavailableException("The document store cannot be reached; try again later");
        }
    }
}
