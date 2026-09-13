package com.thiru.wealthlens.portfolio.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * What a wipe actually managed to delete.
 *
 * <p>Exists because the previous implementation caught each failure, logged it and returned
 * "records and transactions deleted successfully" regardless — telling a user their data was gone
 * while it was still there. A log line is not a reply.
 */
@Data
@NoArgsConstructor
@ToString
public class ErasureReport {

    /** Collections cleared, in the order they were attempted. */
    private List<String> cleared = new ArrayList<>();

    /** Collections that could not be cleared. Empty when the wipe was complete. */
    private List<String> failed = new ArrayList<>();

    public boolean isComplete() {
        return failed.isEmpty();
    }

    public void recordCleared(String collection) {
        cleared.add(collection);
    }

    public void recordFailed(String collection) {
        failed.add(collection);
    }
}
