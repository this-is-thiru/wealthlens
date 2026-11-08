package com.thiru.wealthlens.shared.privacy;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

/**
 * Creates the processing log's indexes: by time, which the daily erasure reads, and by principal, which an account's erasure
 * reads (W-237e). A failure stops startup, as the other initializers' do.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PrivacyIndexInitializer {

    private static final List<Class<?>> ENTITIES = List.of(ProcessingRecord.class, PrivacyConsent.class);

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());
        for (Class<?> entity : ENTITIES) {
            resolver.resolveIndexFor(entity).forEach(index -> mongoTemplate.indexOps(entity).createIndex(index));
            log.info("Ensured the indexes of {}", entity.getSimpleName());
        }
    }
}
