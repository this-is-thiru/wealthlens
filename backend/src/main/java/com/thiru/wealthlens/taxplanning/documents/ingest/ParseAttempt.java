package com.thiru.wealthlens.taxplanning.documents.ingest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One try at reading a document; reading it again is a new attempt, and the attempts are its history ({@code DR 05} M4,
 * {@code DR 14} O3). A parser never throws on a bad document: the failure is this value ({@code DR 07} X2.5), with its
 * reason, and the document is kept for a later parser ({@code DR 14} O9).
 *
 * @param unmappedText what could not be read into a field, verbatim
 * @param parserNotes for a failed attempt, at least its reason
 */
public record ParseAttempt(String attemptId, String documentId, ParserRun run, ParseOutcome outcome,
        List<ExtractedField> extractedFields, List<String> unmappedText, List<String> parserNotes) {

    public ParseAttempt {
        extractedFields = List.copyOf(extractedFields);
        unmappedText = List.copyOf(unmappedText);
        parserNotes = List.copyOf(parserNotes);
        Checks.text(attemptId, "An attempt needs its id");
        Checks.text(documentId, "An attempt names its document");
        Checks.present(run, "An attempt names its parser run");
        Checks.present(outcome, "An attempt needs its outcome");
        Set<String> paths = new HashSet<>();
        for (ExtractedField field : extractedFields) {
            if (!paths.add(field.fieldPath())) {
                throw new IllegalArgumentException("An attempt reads each field once: " + field.fieldPath() + " twice");
            }
        }
        if (outcome == ParseOutcome.FAILED && (parserNotes.isEmpty() || !extractedFields.isEmpty())) {
            throw new IllegalArgumentException("A failed attempt says why and reads no field");
        }
        if (outcome == ParseOutcome.SUCCESS && !unmappedText.isEmpty()) {
            throw new IllegalArgumentException("An attempt that left text unread is partial, not a success");
        }
    }
}
