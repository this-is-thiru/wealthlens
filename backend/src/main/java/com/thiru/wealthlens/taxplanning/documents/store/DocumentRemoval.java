package com.thiru.wealthlens.taxplanning.documents.store;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Deletes documents with everything read from them — their attempts and the confirmations of those attempts — so nothing a
 * certificate said outlives the certificate (W-131a, D-761). Profile erasure and retention both come through here.
 */
@Component
@RequiredArgsConstructor
public class DocumentRemoval {

    private final StoredDocumentRepository documents;
    private final StoredParseAttemptRepository attempts;
    private final StoredConfirmationRepository confirmations;
    private final DocumentBytes bytes;

    /** Every document the profile holds. */
    public void removeUnder(String profileId) {
        removeAll(documents.findByProfileId(profileId));
    }

    /**
     * Confirmations first, then attempts, then the documents: a run cut off part-way leaves no confirmation without its attempt.
     * The bytes go last: a {@code tax_document_bytes} row, in the same transaction as everything above
     * (W-337f, D-951).
     */
    public void removeAll(List<StoredDocument> removed) {
        if (removed.isEmpty()) {
            return;
        }
        List<StoredParseAttempt> read = attempts.findByDocumentIdIn(removed.stream().map(StoredDocument::getId).toList());
        confirmations.deleteAllById(read.stream().map(StoredParseAttempt::getId).toList());
        attempts.deleteAll(read);
        documents.deleteAll(removed);
        bytes.remove(removed.stream().map(StoredDocument::getId).toList());
    }
}
