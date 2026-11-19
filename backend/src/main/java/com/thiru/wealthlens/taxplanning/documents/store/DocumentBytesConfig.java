package com.thiru.wealthlens.taxplanning.documents.store;

import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.taxplanning.documents.parser.IsolatedPdfRead;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/** Gives {@link DocumentBytes} a bucket of its own, rather than replacing the application's GridFS template (W-131b, D-762 C2a). */
@Configuration
@Log4j2
public class DocumentBytesConfig {

    static final String BUCKET = "tax_document_bytes";

    @Bean
    public DocumentBytes documentBytes(MongoDatabaseFactory factory, MongoConverter converter, FieldCipher cipher, DocumentSettings settings,
            IsolatedPdfRead reader) {
        if (!settings.scan().enabled()) {
            // Once per start, so an operator reading the log knows every upload is stored unscanned (W-131h C1a).
            log.warn("The malware scan is off: uploads are stored unscanned (W-131h)");
        }
        return new DocumentBytes(new GridFsTemplate(factory, converter, BUCKET), cipher, settings, new MalwareScan(settings.scan()), reader);
    }
}
