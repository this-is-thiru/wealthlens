package com.thiru.wealthlens.taxengine.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * The startup wiring (D-210): the context compiles the shipped packs as it starts, and the years its manifest ships
 * are exactly the years a lock is shipped for (D-268).
 */
@SpringJUnitConfig(TaxEngineConfig.class)
class TaxEngineConfigTest {

    private static final Path RULEPACKS = Path.of("src/main/resources/rulepacks");

    @Test
    void shippedRulePacks_whenTheContextStarts_servesEveryShippedYear(@Autowired ShippedRulePacks packs) {
        // Then
        assertThat(packs.availableYears()).containsExactlyInAnyOrder(TaxYearId.of(2025), TaxYearId.of(2026));
    }

    @Test
    void shippedYears_whenComparedWithTheLocksShipped_areTheSame() throws IOException {
        // Given
        Set<String> locked;
        try (Stream<Path> files = Files.list(RULEPACKS)) {
            locked = files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".lock.json"))
                    .map(name -> name.substring(0, name.length() - ".lock.json".length()))
                    .collect(Collectors.toSet());
        }

        // When
        Set<String> shipped = ClasspathRulePackProvider.shippedYears(TaxEngineConfig.ROOT).stream()
                .map(TaxYearId::label)
                .collect(Collectors.toSet());

        // Then
        assertThat(shipped).isEqualTo(locked);
    }
}
