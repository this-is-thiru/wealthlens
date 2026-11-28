package com.thiru.wealthlens.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class JpaAuditingConfigTest {

    @Test
    void utcDateTimeProvider_whenTheClockReadsInKolkata_isTheUtcTime() {
        // Given — midnight of 1 February 2026 in Kolkata is 18:30 on 31 January in UTC (IST = UTC + 05:30)
        Clock kolkata = Clock.fixed(Instant.parse("2026-01-31T18:30:00Z"), ZoneId.of("Asia/Kolkata"));

        // When
        Object now = new JpaAuditingConfig().utcDateTimeProvider(kolkata).getNow().orElseThrow();

        // Then
        assertThat(now).isEqualTo(LocalDateTime.of(2026, 1, 31, 18, 30));
    }
}
