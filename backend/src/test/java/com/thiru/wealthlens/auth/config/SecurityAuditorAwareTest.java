package com.thiru.wealthlens.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** The author auditing stamps on a row (W-257): a job's own name inside {@code runAs}, and never after it. */
class SecurityAuditorAwareTest {

    private final SecurityAuditorAware auditor = new SecurityAuditorAware();

    @AfterEach
    void clearTheCaller() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runAs_whenWorkRuns_auditorIsTheActor() {
        // Given
        AtomicReference<Optional<String>> seen = new AtomicReference<>();

        // When
        auditor.runAs("auto-archive", () -> seen.set(auditor.getCurrentAuditor()));

        // Then
        assertThat(seen.get()).contains("auto-archive");
    }

    @Test
    void runAs_whenWorkThrows_auditorIsClearedAndTheFailurePropagates() {
        // Given
        Runnable failing = () -> {
            throw new IllegalStateException("job failed");
        };

        // When
        assertThatThrownBy(() -> auditor.runAs("retention-purge", failing))

                // Then
                .isInstanceOf(IllegalStateException.class).hasMessage("job failed");
        assertThat(auditor.getCurrentAuditor()).contains("unknown");
    }

    @Test
    void getCurrentAuditor_whenSignedInOutsideRunAs_returnsTheName() {
        // Given
        auditor.runAs("staleness-sweep", () -> { });
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("a@b.in", null, List.of()));

        // When
        Optional<String> actor = auditor.getCurrentAuditor();

        // Then
        assertThat(actor).contains("a@b.in");
    }
}
