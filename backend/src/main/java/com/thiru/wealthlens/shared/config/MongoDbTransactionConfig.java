package com.thiru.wealthlens.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Enables MongoDB multi-document ACID transactions when
 * {@code app.mongodb.transactions-enabled=true} (the default, because the
 * MongoDB Atlas cluster used has a replica set).
 *
 * <p>When this property is {@code false} the {@link MongoTransactionManager} bean
 * is not created and all @{@link org.springframework.transaction.annotation.Transactional}
 * annotations become no-ops.
 *
 * <p>{@code @Primary} since {@code PostgresJpaConfig} (W-337a) can add a second
 * {@link org.springframework.transaction.PlatformTransactionManager} bean to the context: every existing
 * {@code @Transactional} in the app resolves by type with no manager named, so this one must stay the default.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mongodb", name = "transactions-enabled", havingValue = "true", matchIfMissing = true)
public class MongoDbTransactionConfig {

    @Bean
    @Primary
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
