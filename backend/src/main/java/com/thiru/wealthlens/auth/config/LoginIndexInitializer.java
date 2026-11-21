package com.thiru.wealthlens.auth.config;

import com.thiru.wealthlens.auth.entity.RefreshToken;
import com.thiru.wealthlens.auth.entity.UserDetail;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

/**
 * Creates the login's unique index on the stored email, which keeps one account per email now that the email is no longer the id
 * (W-253 C2a), and the refresh tokens' indexes: the hash unique, the expiry removing a row once it passes (W-358b). A failure stops
 * startup, as the other initializers' do.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class LoginIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());
        for (Class<?> type : List.of(UserDetail.class, RefreshToken.class)) {
            resolver.resolveIndexFor(type).forEach(index -> mongoTemplate.indexOps(type).createIndex(index));
            log.info("Ensured the indexes of {}", type.getSimpleName());
        }
    }
}
