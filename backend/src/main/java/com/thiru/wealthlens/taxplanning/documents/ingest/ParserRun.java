package com.thiru.wealthlens.taxplanning.documents.ingest;

import java.time.LocalDateTime;

/** Which parser read a document, and when ({@code DR 05} M4; {@code DR 07} X2.5). */
public record ParserRun(String parserId, String parserVersion, LocalDateTime startedAt, LocalDateTime finishedAt) {

    public ParserRun {
        Checks.text(parserId, "A parse names its parser");
        Checks.text(parserVersion, "A parse names its parser's version");
        Checks.present(startedAt, "A parse needs the time it started");
        Checks.present(finishedAt, "A parse needs the time it finished");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("A parse cannot finish before it started");
        }
    }
}
