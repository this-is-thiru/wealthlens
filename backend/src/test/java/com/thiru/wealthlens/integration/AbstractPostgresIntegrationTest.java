package com.thiru.wealthlens.integration;

import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Adds a PostgreSQL 18 container beside {@link AbstractIntegrationTest}'s MongoDB one, for W-337a's probe only (D-937) —
 * kept out of the shared base so the other 79 integration classes pay nothing for a container they do not use.
 * {@code app.postgres.enabled} turns on {@code PostgresJpaConfig}, which is off by default everywhere else.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractPostgresIntegrationTest extends AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES_CONTAINER;
    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer("postgres:18");
        POSTGRES_CONTAINER.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("app.postgres.enabled", () -> "true");
        registry.add("app.postgres.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("app.postgres.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("app.postgres.datasource.password", POSTGRES_CONTAINER::getPassword);
    }
}
