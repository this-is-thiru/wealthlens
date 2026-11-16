package com.thiru.wealthlens.taxplanning.documents.parser;

import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gathers the parsers at start, so two for one form fail the start, not an upload (W-125). */
@Configuration
public class DocumentParsersConfig {

    @Bean
    public DocumentParsers documentParsers(List<DocumentParser> parsers, Clock clock) {
        return DocumentParsers.of(parsers, clock);
    }
}
