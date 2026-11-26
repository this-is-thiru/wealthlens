package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CopierRunTest {

    @Test
    void requireSpecified_whenACollectionHasNoCopySpecified_refusesBeforeReadingAnything() {
        // Given — a source collection the copier has no copy for (every m1.6.1 collection is specified since W-337g2b3, so
        // the guard is shown with a set of its own)
        Set<String> unspecified = Set.of("profit_and_loss");

        // When / Then
        assertThatThrownBy(() -> CopierRun.requireSpecified(unspecified)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The copier has no copy specified for [profit_and_loss]; nothing was copied");
    }
}
