package com.thiru.wealthlens.auth.config;

import com.thiru.wealthlens.auth.entity.UserDetail;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

/**
 * Creates the login's unique index on the stored email, which keeps one account per email now that the email is no longer the id
 * (W-253 C2a). A failure stops startup, as the other initializers' do.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class LoginIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext()).resolveIndexFor(UserDetail.class)
                .forEach(index -> mongoTemplate.indexOps(UserDetail.class).createIndex(index));
        log.info("Ensured the indexes of {}", UserDetail.class.getSimpleName());
    }
}
