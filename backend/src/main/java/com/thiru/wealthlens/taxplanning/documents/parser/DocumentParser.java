package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;

/**
 * Reads one form ({@code DR 07} X2.5; W-125). One parser per form, with no tax year: which form a year prescribes is law, so it
 * is not written here (D-728 C1a). The bytes are untrusted input from an unknown generator ({@code DR 14} O6): a document
 * that cannot be read — a password, no text layer, a broken file — is a {@link com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome#FAILED}
 * reading with its reason, never an exception ({@code DR 13} rows 71 – 72). The run, its id and its times are
 * {@link DocumentParsers}', so a parser is a function of the bytes alone (D-728 C2a).
 */
public interface DocumentParser {

    /** The form this parser reads. */
    DocumentType reads();

    /** Names the parser on every attempt it makes. */
    String id();

    /** Changes when what the parser reads changes, so an attempt can be repeated by a later version. */
    String version();

    DocumentReading read(DocumentContent content);
}
