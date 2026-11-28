package com.thiru.wealthlens.shared.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing for every entity: who wrote a row ({@code SecurityAuditorAware}) and when, in UTC. The store itself —
 * the pool, the entity manager, the transaction manager, the repositories — is Spring Boot's auto-configuration from
 * {@code spring.datasource} and {@code spring.jpa} (W-426).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware", dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * Audit times in UTC (W-402, D-959 C1a): with no provider, Spring Data's {@code CurrentDateTimeProvider} takes
     * {@code LocalDateTime.now()}, the JVM's zone, while every other stored time is UTC.
     */
    @Bean
    DateTimeProvider utcDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
    }
}
