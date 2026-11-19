package com.thiru.wealthlens.taxplanning.documents.store;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
     * The bytes go last, after the transaction commits: GridFS takes no part in it, so bytes removed inside a transaction that
     * then rolled back would leave a document whose bytes are gone (D-762 C4a). Bytes a failed removal leaves behind are
     * swept by retention once no document names them.
     */
    public void removeAll(List<StoredDocument> removed) {
        if (removed.isEmpty()) {
            return;
        }
        List<StoredParseAttempt> read = attempts.findByDocumentIdIn(removed.stream().map(StoredDocument::getId).toList());
        confirmations.deleteAllById(read.stream().map(StoredParseAttempt::getId).toList());
        attempts.deleteAll(read);
        documents.deleteAll(removed);
        List<String> ids = removed.stream().map(StoredDocument::getId).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bytes.remove(ids);
                }
            });
        } else {
            bytes.remove(ids);
        }
    }
}
