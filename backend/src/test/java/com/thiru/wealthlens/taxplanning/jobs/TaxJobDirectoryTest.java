package com.thiru.wealthlens.taxplanning.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.taxplanning.gazette.GazetteWatch;
import com.thiru.wealthlens.taxplanning.gazette.GazetteWatchSettings;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class TaxJobDirectoryTest {

    @Mock
    private TaxJobRunner runner;

    @Mock
    private TaxJob logRetention;

    @Mock
    private GazetteWatch gazetteWatch;

    @Test
    void run_whenAJobHasTheName_runsItThroughTheRunner() {
        // Given
        when(logRetention.name()).thenReturn("log-retention");
        when(runner.run(logRetention)).thenReturn(true);

        // When
        JobRunReceipt receipt = directory(false).run("log-retention");

        // Then
        assertThat(receipt).isEqualTo(new JobRunReceipt("log-retention", true));
        verify(runner).run(logRetention);
    }

    @Test
    void run_whenAnotherRunHoldsTheLease_saysItDidNotRun() {
        // Given
        when(logRetention.name()).thenReturn("log-retention");
        when(runner.run(logRetention)).thenReturn(false);

        // When
        JobRunReceipt receipt = directory(false).run("log-retention");

        // Then
        assertThat(receipt.ran()).isFalse();
    }

    @Test
    void run_whenNoJobHasTheName_refusesNamingTheJobs() {
        // Given
        when(logRetention.name()).thenReturn("log-retention");
        when(gazetteWatch.name()).thenReturn("gazette-watch");

        // When / Then
        assertThatThrownBy(() -> directory(false).run("nightly-cleanup"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tax job is named nightly-cleanup; the jobs are [gazette-watch, log-retention]");
        verify(runner, never()).run(any());
    }

    @Test
    void run_whenTheGazetteWatchIsOff_refusesWithoutRunningIt() {
        // Given
        when(logRetention.name()).thenReturn("log-retention");
        when(gazetteWatch.name()).thenReturn("gazette-watch");

        // When / Then
        assertThatThrownBy(() -> directory(false).run("gazette-watch"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.tax.gazette-watch.enabled");
        verify(runner, never()).run(any());
    }

    @Test
    void run_whenTheGazetteWatchIsOn_runsIt() {
        // Given
        when(logRetention.name()).thenReturn("log-retention");
        when(gazetteWatch.name()).thenReturn("gazette-watch");
        when(runner.run(gazetteWatch)).thenReturn(true);

        // When
        JobRunReceipt receipt = directory(true).run("gazette-watch");

        // Then
        assertThat(receipt).isEqualTo(new JobRunReceipt("gazette-watch", true));
    }

    private TaxJobDirectory directory(boolean gazetteWatchOn) {
        GazetteWatchSettings settings = new GazetteWatchSettings(gazetteWatchOn, "https://example.invalid", "test",
                Duration.ofSeconds(1), Duration.ZERO, 1, 1, DataSize.ofMegabytes(1), List.of(), List.of());
        return new TaxJobDirectory(List.of(logRetention, gazetteWatch), runner, settings);
    }
}
