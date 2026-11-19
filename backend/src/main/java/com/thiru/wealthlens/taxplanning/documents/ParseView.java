package com.thiru.wealthlens.taxplanning.documents;

import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import java.util.List;
import java.util.Optional;

/**
 * What was read from a document, for its owner to check and confirm (W-131d1, D-765 C1a). The employee's PAN is reported only as
 * whether it matches the profile's (C3a).
 *
 * @param reading the latest attempt; none while the document waits to be read
 */
public record ParseView(String documentId, State state, Optional<Reading> reading, PanCheck employeePan, boolean confirmed) {

    /** Whether the document has been read yet. */
    public enum State {
        WAITING,
        READ
    }

    /** The employee PAN read (or corrected) against the profile's (B9-73). */
    public enum PanCheck {
        MATCHES,
        DIFFERS,
        NOT_READ
    }

    /** One attempt's result. */
    public record Reading(String attemptId, ParseOutcome outcome, List<Field> fields, List<String> unmappedText, List<String> notes) {

        static Reading of(ParseAttempt attempt) {
            return new Reading(attempt.attemptId(), attempt.outcome(), attempt.extractedFields().stream().map(Field::of).toList(),
                    attempt.unmappedText(), attempt.parserNotes());
        }
    }

    /**
     * One row read.
     *
     * @param value the value as text, in the form a correction is written: rupees, {@code yes}/{@code no}, {@code from/to}, words
     */
    public record Field(String path, Optional<String> rawText, Optional<String> value, double confidence, Optional<Integer> page) {

        static Field of(ExtractedField field) {
            return new Field(field.fieldPath(), field.rawText(), field.interpretedValue().map(Field::text), field.confidence(),
                    field.location().map(location -> location.pageNumber()));
        }

        static String text(ExtractedValue value) {
            return switch (value) {
                case ExtractedValue.MoneyRead money -> money.amount().toString();
                case ExtractedValue.FlagRead flag -> flag.value() ? "yes" : "no";
                case ExtractedValue.PeriodRead period -> period.from() + "/" + period.to();
                case ExtractedValue.TextRead words -> words.text();
            };
        }
    }
}
