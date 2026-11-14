package com.thiru.wealthlens.taxplanning.workspace;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

/** Creates TS T7's indexes for the workspace collections, which are new; a failure stops startup, as the profile family's does. */
@Log4j2
@Component
@RequiredArgsConstructor
public class WorkspaceIndexInitializer {

    private static final List<Class<?>> ENTITIES = List.of(TaxYearWorkspace.class, FilingDraft.class, DraftRevision.class,
            RegimeElectionHistory.class);

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
