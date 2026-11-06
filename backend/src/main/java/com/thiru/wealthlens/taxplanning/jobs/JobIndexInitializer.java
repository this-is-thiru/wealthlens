package com.thiru.wealthlens.taxplanning.jobs;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

/** The lease's TTL index, which lets Mongo remove a lease a crashed run never released. */
@Log4j2
@Component
@RequiredArgsConstructor
public class JobIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());
        resolver.resolveIndexFor(JobLease.class).forEach(index -> mongoTemplate.indexOps(JobLease.class).createIndex(index));
        log.info("Ensured the indexes of {}", JobLease.class.getSimpleName());
    }
}
