package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.engine.ChargeCalculator;
import com.thiru.wealthlens.brokercharges.engine.ChargeCalculatorRegistry;
import com.thiru.wealthlens.brokercharges.engine.ChargeEngine;
import java.util.Arrays;
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

    @Autowired
    private ChargeCalculatorRegistry chargeCalculatorRegistry;

    @Autowired
    private ChargeEngine chargeEngine;

    @Test
    void applicationContext_whenEveryBeanIsWired_starts() {
        // Given / When — reaching this method at all means refresh() completed

        // Then
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isPositive();
    }

    @Test
    void chargeCalculatorRegistry_isWiredWithADistinctCalculatorForEveryBasis() {
        // Given — the registry refuses to construct if a basis is unserved, so reaching this test
        // already proves the wiring. Stated anyway, because the failure it would otherwise produce
        // is a startup stack trace with no mention of what was actually missing.

        // When
        var calculators = Arrays.stream(ChargeBasis.values()).map(chargeCalculatorRegistry::get).toList();

        // Then — one per basis, none shared, each declaring the basis it was fetched under
        assertThat(calculators).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(calculators).extracting(ChargeCalculator::basis)
                .containsExactly(ChargeBasis.values());
    }

    @Test
    void chargeEngine_isWiredWithBothResolvers() {
        // Given — the engine became a bean only once both resolvers had implementations. Asserting
        // the injection is what distinguishes "it starts" from "it starts and could actually price
        // a trade".

        // When / Then
        assertThat(chargeEngine).isNotNull();
    }
}
