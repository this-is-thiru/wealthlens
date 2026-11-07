package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxplanning.gazette.GazetteLead;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

/**
 * The lease's TTL index, which lets Mongo remove a lease a crashed run never released; and the Gazette watch's unique lead
 * URL, which makes a second run over the same number add nothing (W-236).
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class JobIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());
        for (Class<?> entity : List.of(JobLease.class, GazetteLead.class)) {
            resolver.resolveIndexFor(entity).forEach(index -> mongoTemplate.indexOps(entity).createIndex(index));
            log.info("Ensured the indexes of {}", entity.getSimpleName());
        }
    }
}
