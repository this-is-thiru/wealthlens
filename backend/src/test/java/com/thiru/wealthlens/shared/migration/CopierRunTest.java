package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class CopierRunTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    @Mock
    private MongoCopier copier;

    @Mock
    private Environment environment;

    @Mock
    private ApplicationContext context;

    @InjectMocks
    private CopierRun run;

    @Test
    void run_whenEveryCopyIsProven_checksTheSourceCopiesEverythingInTheWrittenZoneThenExits() {
        // Given — the owner states the zone the source was written in (D-958)
        given(environment.getRequiredProperty(CopierRun.WRITTEN_ZONE)).willReturn("Asia/Kolkata");
        given(copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA)).willReturn(List.of(report("tax_profiles", List.of())));

        // When
        run.run(null);

        // Then — the unknown-collection check first, then the copy, then the exit (which reads the exit-code generators)
        InOrder order = inOrder(copier, context);
        order.verify(copier).requireKnownSource();
        order.verify(copier).copyAll(CollectionCopies.SPECIFIED, KOLKATA);
        order.verify(context).getBeansOfType(ExitCodeGenerator.class);
    }

    @Test
    void run_whenACopyIsNotProven_refusesNamingItAndDoesNotExitCleanly() {
        // Given
        given(environment.getRequiredProperty(CopierRun.WRITTEN_ZONE)).willReturn("Asia/Kolkata");
        given(copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA)).willReturn(List.of(report("tax_profiles", List.of()),
                report("assets", List.of("rows differ from the source: asset-1"))));

        // When / Then — a non-zero exit, the unproven source named
        assertThatThrownBy(() -> run.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The copy is not proven for [assets]; see each one's line above");
        verifyNoInteractions(context);
    }

    @Test
    void requireSpecified_whenACollectionHasNoCopySpecified_refusesBeforeReadingAnything() {
        // Given — a source collection the copier has no copy for (every m1.6.1 collection is specified since W-337g2b3, so
        // the guard is shown with a set of its own)
        Set<String> unspecified = Set.of("profit_and_loss");

        // When / Then
        assertThatThrownBy(() -> CopierRun.requireSpecified(unspecified)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The copier has no copy specified for [profit_and_loss]; nothing was copied");
    }

    private static CopyReport report(String source, List<String> problems) {
        return new CopyReport(source, source, 1, 1, "digest", "", problems);
    }
}
