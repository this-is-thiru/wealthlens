package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class CopierRunTest {

    @Mock
    private MongoCopier copier;

    @Mock
    private Environment environment;

    @Mock
    private ApplicationContext context;

    @InjectMocks
    private CopierRun run;

    @Test
    void run_whenACollectionHasNoCopySpecified_refusesBeforeReadingAnything() {
        // Given — 31 of the 40 specified (W-337g1, W-337g2a1 – a3); the 9 left, with JSON columns, are W-337g2b's

        // When / Then
        assertThatThrownBy(() -> run.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The copier has no copy specified for [assets, charge_instruments");
        verifyNoInteractions(copier, environment, context);
    }
}
