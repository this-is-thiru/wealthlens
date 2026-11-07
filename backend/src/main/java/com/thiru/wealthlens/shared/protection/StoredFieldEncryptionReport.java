package com.thiru.wealthlens.shared.protection;

import java.util.List;

/**
 * What a run of {@link StoredFieldEncryption} found and did. {@code plainRows} counts rows holding a plain field;
 * {@code encryptedRows} those this run changed, which is 0 on a dry run and fewer than found when a row changed
 * underneath the run (the next run takes it).
 */
public record StoredFieldEncryptionReport(boolean dryRun, String caller, List<CollectionCount> collections) {

    public StoredFieldEncryptionReport {
        collections = List.copyOf(collections);
    }

    public record CollectionCount(String collection, int plainRows, int encryptedRows) {
    }
}
