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
        // Given — W-337g1 specifies 22 of the 40; the 18 with embedded data are W-337g2's

        // When / Then
        assertThatThrownBy(() -> run.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The copier has no copy specified for [assets, charge_accounts");
        verifyNoInteractions(copier, environment, context);
    }
}
