package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.WealthLensApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The copier started as the owner starts it (W-409, D-984): the whole application under the {@code copier} profile, not the
 * copier bean inside the web test context. Runbook §0.9 step 3 failed at start (D-982 (4)) while every other test passed,
 * because none started the application this way. Empty stores on both sides: the run copies nothing, proves it and stops.
 */
@Tag("integration")
@Isolated
class CopierStartIntegrationTest {

    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0.4").withReplicaSet();
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @BeforeAll
    static void startStores() {
        MONGO.start();
        POSTGRES.start();
    }

    @AfterAll
    static void stopStores() {
        MONGO.stop();
        POSTGRES.stop();
    }

    @Test
    void run_whenStartedUnderTheCopierProfile_copiesAndStopsWithNoPortOpen() {
        // Given
        // Command-line arguments, as runbook §0.9 passes them: they outrank the profile yamls
        String[] arguments = {"--spring.mongodb.uri=" + MONGO.getReplicaSetUrl(),
            "--app.postgres.enabled=true",
            "--app.postgres.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--app.postgres.datasource.username=" + POSTGRES.getUsername(),
            "--app.postgres.datasource.password=" + POSTGRES.getPassword(),
            "--app.tax.documents.scan.host=localhost",
            "--app.tax.documents.scan.port=3310",
            "--" + CopierRun.WRITTEN_ZONE + "=Asia/Kolkata"};
        SpringApplicationBuilder application = new SpringApplicationBuilder(WealthLensApplication.class).profiles("integration-test", "copier");

        // When
        ConfigurableApplicationContext context = application.run(arguments);

        // Then
        assertThat(context.isActive()).as("CopierRun stops the application once the copy is proven").isFalse();
        assertThat(context.getEnvironment().getProperty("local.server.port")).as("no port is opened while it copies").isEqualTo("-1");
    }
}
