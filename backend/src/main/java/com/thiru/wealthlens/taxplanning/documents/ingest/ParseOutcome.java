package com.thiru.wealthlens.taxplanning.documents.ingest;

/** How a try at reading a document ended ({@code DR 05} M4). */
public enum ParseOutcome {
    /** Every text the parser met was read into a field. */
    SUCCESS,
    /** Some fields were read; some text could not be, and is kept verbatim. */
    PARTIAL,
    /** Nothing was read; the attempt says why, and the document is kept for a later parser ({@code DR 14} O9). */
    FAILED
}
