package com.thiru.wealthlens.taxplanning.documents.store;

import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.taxplanning.documents.parser.IsolatedPdfRead;
import java.time.Clock;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds {@link DocumentBytes}, whose {@link MalwareScan} needs {@code settings.scan()} rather than the whole of
 * {@link DocumentSettings} (W-131b; table instead of a GridFS bucket since W-337f, D-951).
 */
@Configuration
@Log4j2
public class DocumentBytesConfig {

    @Bean
    public DocumentBytes documentBytes(TaxDocumentBytesRepository repository, FieldCipher cipher, DocumentSettings settings,
            IsolatedPdfRead reader) {
        if (!settings.scan().enabled()) {
            // Once per start, so an operator reading the log knows every upload is stored unscanned (W-131h C1a).
            log.warn("The malware scan is off: uploads are stored unscanned (W-131h)");
        }
        // Stamped with the machine's time, as GridFS's uploadDate was: orphans are aged against when the bytes truly
        // arrived (D-762 C5a), whatever the app's clock reads.
        return new DocumentBytes(repository, cipher, settings, new MalwareScan(settings.scan()), Clock.systemUTC(), reader);
    }
}
