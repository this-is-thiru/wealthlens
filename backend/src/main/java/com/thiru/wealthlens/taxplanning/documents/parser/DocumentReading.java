package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import java.util.List;

/**
 * What a parser read from one document. Whether the outcome fits what was read is checked once, when
 * {@link DocumentParsers} makes the attempt from it (D-727).
 *
 * @param unmappedText what could not be read into a field, verbatim
 * @param parserNotes for a failed reading, at least its reason
 */
public record DocumentReading(ParseOutcome outcome, List<ExtractedField> extractedFields, List<String> unmappedText,
        List<String> parserNotes) {

    public DocumentReading {
        if (outcome == null) {
            throw new IllegalArgumentException("A reading needs its outcome");
        }
        extractedFields = List.copyOf(extractedFields);
        unmappedText = List.copyOf(unmappedText);
        parserNotes = List.copyOf(parserNotes);
    }
}
