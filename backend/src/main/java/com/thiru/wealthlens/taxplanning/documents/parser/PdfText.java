package com.thiru.wealthlens.taxplanning.documents.parser;

import java.util.List;

/**
 * What the isolated read of an uploaded PDF found (W-131f): the reply {@link PdfReadChild} writes and {@link IsolatedPdfRead}
 * checks. It carries only what the application needs from the untrusted bytes, so nothing else crosses the process boundary.
 *
 * @param pages the page count, when {@code outcome} is {@code READ}
 * @param words the printed words, when the read asked for them and {@code outcome} is {@code READ}
 * @param reason why the PDF could not be read, when {@code outcome} is {@code UNREADABLE}; otherwise empty
 */
public record PdfText(Outcome outcome, int pages, List<PrintedText.Word> words, String reason) {

    public PdfText {
        words = List.copyOf(words);
    }

    public enum Outcome {
        READ,
        PASSWORD,
        UNREADABLE
    }

    /** What the child is asked to read: the page count alone at upload, the words too in the parse job. */
    public enum Depth {
        PAGES,
        WORDS
    }
}
