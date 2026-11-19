package com.thiru.wealthlens.taxplanning.documents.ingest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * The person's own act on one attempt: nothing read is used until it exists ({@code DR 05} M4). An accepted value may differ
 * from the one read — it is what the person says the form holds.
 *
 * @param acceptedFields by field path
 * @param rejectedFields field paths
 * @param confirmedAt UTC
 */
public record DocumentConfirmation(String attemptId, String confirmedBy, LocalDateTime confirmedAt,
        Map<String, ExtractedValue> acceptedFields, Set<String> rejectedFields) {

    public DocumentConfirmation {
        acceptedFields = Map.copyOf(acceptedFields);
        rejectedFields = Set.copyOf(rejectedFields);
        Checks.text(attemptId, "A confirmation names its attempt");
        Checks.text(confirmedBy, "A confirmation names who confirmed");
        Checks.present(confirmedAt, "A confirmation needs the time it was made");
        for (String path : rejectedFields) {
            if (acceptedFields.containsKey(path)) {
                throw new IllegalArgumentException("A field is accepted or rejected, not both: " + path);
            }
        }
    }
}
