package com.thiru.wealthlens.taxplanning.documents.ingest;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * One uploaded document, as received ({@code DR 05} M4). Same bytes, same {@code sha256}: an upload is idempotent on it
 * ({@code DR 05} M7). Where the bytes are kept is the upload's (W-131), so no storage reference is held here.
 *
 * @param uploadedAt UTC
 */
public record IngestedDocument(String documentId, String profileId, TaxYearId taxYear, DocumentType documentType,
        String sha256, LocalDateTime uploadedAt, int pageCount) {

    /** A SHA-256 digest as lowercase hex: 256 bits, 64 characters. */
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    public IngestedDocument {
        Checks.text(documentId, "A document needs its id");
        Checks.text(profileId, "A document belongs to a profile");
        Checks.present(taxYear, "A document belongs to a tax year");
        Checks.present(documentType, "A document needs its type");
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("A document's SHA-256 is 64 lowercase hexadecimal characters");
        }
        Checks.present(uploadedAt, "A document needs the time it was uploaded");
        if (pageCount < 1) {
            throw new IllegalArgumentException("A document has at least one page");
        }
    }
}
