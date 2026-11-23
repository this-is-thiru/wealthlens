package com.thiru.wealthlens.shared.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * PostgreSQL on Neon/Aiven, for the two parallel entities proving the W-337 mapping (W-337a, D-937): off by default
 * ({@code app.postgres.enabled=false}), so nothing about the live app changes until a test or a run explicitly turns it on.
 * Built by hand rather than left to Spring Boot's own JPA auto-configuration, because this process is already a MongoDB
 * application: letting Boot auto-configure "the" {@code DataSource}/{@code EntityManagerFactory}/transaction manager would
 * collide with it, so every bean here is explicitly named and the entity manager factory is scoped to only the
 * {@code auth}/{@code insurance} JPA entities (W-337c's later port, not every one of the 40 collections).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.postgres", name = "enabled", havingValue = "true")
@EnableJpaRepositories(
        basePackages = {"com.thiru.wealthlens.auth.repository", "com.thiru.wealthlens.insurance.repository"},
        entityManagerFactoryRef = "postgresEntityManagerFactory",
        transactionManagerRef = "postgresTransactionManager")
public class PostgresJpaConfig {

    @Bean
    @ConfigurationProperties("app.postgres.datasource")
    DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    DataSource postgresDataSource(DataSourceProperties postgresDataSourceProperties) {
        return postgresDataSourceProperties.initializeDataSourceBuilder().build();
    }

    /**
     * Hibernate instantiates a {@code @Convert} converter by reflection unless told to ask Spring for it instead — the same
     * problem {@code MongoConfig.mongoCustomConversions} solves for {@code @ValueConverter} (D-447), so {@code EncryptedColumn}
     * and {@code SearchableColumn} can take {@code FieldCipher} as a constructor argument rather than build it themselves.
     */
    @Bean
    LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(EntityManagerFactoryBuilder builder,
            DataSource postgresDataSource, ConfigurableListableBeanFactory beanFactory) {
        return builder.dataSource(postgresDataSource)
                .packages("com.thiru.wealthlens.auth.entity", "com.thiru.wealthlens.insurance.entity")
                .persistenceUnit("postgres")
                .properties(Map.of("hibernate.resource.beans.container", new SpringBeanContainer(beanFactory)))
                .build();
    }

    @Bean
    EntityManagerFactoryBuilder postgresEntityManagerFactoryBuilder() {
        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        return new EntityManagerFactoryBuilder(vendorAdapter, dataSource -> Map.of(), null);
    }

    @Bean
    PlatformTransactionManager postgresTransactionManager(EntityManagerFactory postgresEntityManagerFactory) {
        return new JpaTransactionManager(postgresEntityManagerFactory);
    }
}
