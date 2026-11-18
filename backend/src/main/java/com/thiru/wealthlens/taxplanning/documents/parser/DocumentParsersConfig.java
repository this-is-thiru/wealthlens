package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import java.time.Clock;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gathers the parsers at start, so two for one form fail the start, not an upload (W-125). */
@Configuration
@Log4j2
public class DocumentParsersConfig {

    @Bean
    public DocumentParsers documentParsers(List<DocumentParser> parsers, Clock clock) {
        return DocumentParsers.of(parsers, clock);
    }

    @Bean
    public IsolatedPdfRead isolatedPdfRead(DocumentSettings settings) {
        DocumentSettings.Isolation isolation = settings.isolation();
        // Once per start, so the log says how a PDF is isolated and what it is not isolated from (W-131f, C2a's fallback).
        log.info("PDFs are read in a child process: heap {} MiB, CPU {} s, Java sockets to a dead proxy and no name lookups; "
                + "the operating system's network is not cut", isolation.heapMebibytes(), isolation.cpuSeconds());
        return IsolatedPdfRead.of(isolation, settings.parseBudget());
    }
}
