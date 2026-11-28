package com.thiru.wealthlens.shared.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * PostgreSQL on Neon/Aiven (W-337a D-937; live cutover starting W-337c1 D-941 C3a): active by default on branch
 * {@code w-337} ({@code matchIfMissing = true}), since {@code auth} and {@code insurance} now store their live rows in
 * PostgreSQL. Built by hand rather than left to Spring Boot's JPA auto-configuration while MongoDB still serves the
 * remaining modules, so every bean is explicitly named and the entity manager factory scans only the ported packages.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.postgres", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware", dateTimeProviderRef = "utcDateTimeProvider")
@EnableJpaRepositories(
        basePackages = {"com.thiru.wealthlens.auth.repository", "com.thiru.wealthlens.insurance.repository",
                "com.thiru.wealthlens.shared.privacy", "com.thiru.wealthlens.corporate.repository",
                "com.thiru.wealthlens.brokercharges.repository", "com.thiru.wealthlens.portfolio.repository",
                "com.thiru.wealthlens.taxplanning"},
        entityManagerFactoryRef = "postgresEntityManagerFactory",
        transactionManagerRef = "postgresTransactionManager")
public class PostgresJpaConfig {

    @Bean
    @ConfigurationProperties("app.postgres.datasource")
    DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * The pool every PostgreSQL user shares — Flyway and Hibernate — so its
     * {@code hikari.schema} puts them all in schema {@code wealthlens} (W-413, D-989; D-929).
     */
    @Bean
    @ConfigurationProperties("app.postgres.datasource.hikari")
    HikariDataSource postgresDataSource(DataSourceProperties postgresDataSourceProperties) {
        return postgresDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * Validates every entity against its table at start (W-405, D-963): a missing table or column, or a column of the wrong type,
     * stops the app instead of failing one query later. Flyway migrates first, so a database it built always passes.
     *
     * <p>Hibernate instantiates a {@code @Convert} converter by reflection unless told to ask Spring for it instead, so
     * {@code EncryptedColumn} and {@code SearchableColumn} can take {@code FieldCipher} as a constructor argument rather than
     * build it themselves (D-447).
     */
    @Bean
    LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(EntityManagerFactoryBuilder builder,
            DataSource postgresDataSource, ConfigurableListableBeanFactory beanFactory) {
        return builder.dataSource(postgresDataSource)
                .packages("com.thiru.wealthlens.auth.entity", "com.thiru.wealthlens.insurance.entity",
                        "com.thiru.wealthlens.shared.privacy", "com.thiru.wealthlens.corporate.entity",
                        "com.thiru.wealthlens.brokercharges.entity", "com.thiru.wealthlens.portfolio.entity",
                        "com.thiru.wealthlens.taxplanning")
                .persistenceUnit("postgres")
                .properties(Map.of("hibernate.resource.beans.container", new SpringBeanContainer(beanFactory), "hibernate.hbm2ddl.auto", "validate"))
                .build();
    }

    @Bean
    EntityManagerFactoryBuilder postgresEntityManagerFactoryBuilder() {
        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        return new EntityManagerFactoryBuilder(vendorAdapter, dataSource -> Map.of(), null);
    }

    /**
     * Audit times in UTC (W-402, D-959 C1a): with no provider, Spring Data's {@code CurrentDateTimeProvider} takes
     * {@code LocalDateTime.now()}, the JVM's zone, while every other stored time is UTC.
     */
    @Bean
    DateTimeProvider utcDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
    }

    @Bean
    PlatformTransactionManager postgresTransactionManager(EntityManagerFactory postgresEntityManagerFactory) {
        return new JpaTransactionManager(postgresEntityManagerFactory);
    }
}
