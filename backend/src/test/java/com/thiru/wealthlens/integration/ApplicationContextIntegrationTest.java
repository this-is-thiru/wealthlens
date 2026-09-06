package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * The application starts.
 *
 * <p>Nothing asserted this before, so a bean that refuses to construct surfaced only as an obscure
 * {@code BeanCreationException} inside whichever integration test happened to run first — or, when
 * Docker was unavailable, not at all. A component whose constructor validates its collaborators can
 * break startup while every unit test stays green, because unit tests construct it directly.
 */
class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContext_whenEveryBeanIsWired_starts() {
        // Given / When — reaching this method at all means refresh() completed

        // Then
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isPositive();
    }
}
