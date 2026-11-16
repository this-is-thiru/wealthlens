package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one place a document is parsed (W-125): the parser for its form, the run timed and recorded as a {@link ParseAttempt}
 * with a fresh id (D-728 C2a). A time budget for the run is the parse job's (W-131, D-728 C3a).
 *
 * @param byType one parser per form
 * @param clock the application's; attempts are timed in UTC
 */
public record DocumentParsers(Map<DocumentType, DocumentParser> byType, Clock clock) {

    public DocumentParsers {
        byType = Map.copyOf(byType);
    }

    /** One parser per form; two for one form are refused, so the start fails rather than picking one silently. */
    public static DocumentParsers of(List<DocumentParser> parsers, Clock clock) {
        Map<DocumentType, DocumentParser> byType = new EnumMap<>(DocumentType.class);
        for (DocumentParser parser : parsers) {
            DocumentParser earlier = byType.put(parser.reads(), parser);
            if (earlier != null) {
                throw new IllegalStateException(
                        "Two parsers read " + parser.reads() + ": " + earlier.id() + " and " + parser.id());
            }
        }
        return new DocumentParsers(byType, clock);
    }

    /**
     * @throws IllegalArgumentException if the content is not the document's, or no parser reads its form
     */
    public ParseAttempt parse(IngestedDocument document, DocumentContent content) {
        if (!content.sha256().equals(document.sha256())) {
            throw new IllegalArgumentException("The content is not this document's");
        }
        DocumentParser parser = byType.get(document.documentType());
        if (parser == null) {
            throw new IllegalArgumentException("No parser reads " + document.documentType() + " yet");
        }
        Clock utc = clock.withZone(ZoneOffset.UTC);
        LocalDateTime startedAt = LocalDateTime.now(utc);
        DocumentReading reading = parser.read(content, document.taxYear());
        LocalDateTime finishedAt = LocalDateTime.now(utc);
        return new ParseAttempt(UUID.randomUUID().toString(), document.documentId(),
                new ParserRun(parser.id(), parser.version(), startedAt, finishedAt), reading.outcome(),
                reading.extractedFields(), reading.unmappedText(), reading.parserNotes());
    }
}
