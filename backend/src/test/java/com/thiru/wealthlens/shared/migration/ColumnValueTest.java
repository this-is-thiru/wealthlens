package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class ColumnValueTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    @Test
    void of_whenADateAtMidnightUtc_isThatDay() {
        // Given — m1.6.1's LocalDateToDateConverter stored 2024-03-31 as midnight UTC
        Date stored = Date.from(Instant.parse("2024-03-31T00:00:00Z"));

        // When
        Object value = ColumnValue.of(stored, "date", KOLKATA);

        // Then
        assertThat(value).isEqualTo(LocalDate.of(2024, 3, 31));
    }

    @Test
    void of_whenADateIsNotAtMidnightUtc_isRefused() {
        // Given — midnight in Kolkata, which m1.6.1 never wrote for a LocalDate
        Date stored = Date.from(Instant.parse("2024-03-30T18:30:00Z"));

        // When / Then
        assertThatThrownBy(() -> ColumnValue.of(stored, "date", KOLKATA)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not at midnight UTC");
    }

    @Test
    void of_whenATimestampWasWrittenInKolkata_isTheTimeTheAppHeld() {
        // Given — Spring Data stored LocalDateTime 2024-01-01T00:00 from a JVM in Asia/Kolkata as 2023-12-31T18:30Z
        // (probed in session 122 on this branch's Spring Data MongoDB)
        Date stored = Date.from(Instant.parse("2023-12-31T18:30:00Z"));

        // When
        Object value = ColumnValue.of(stored, "timestamp without time zone", KOLKATA);

        // Then
        assertThat(value).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0));
    }

    @Test
    void of_whenAnInstantColumn_keepsTheInstant() {
        // Given
        Date stored = Date.from(Instant.parse("2023-12-31T18:30:00Z"));

        // When
        Object value = ColumnValue.of(stored, "timestamp with time zone", KOLKATA);

        // Then
        assertThat(value).isEqualTo(OffsetDateTime.of(2023, 12, 31, 18, 30, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void of_whenAnObjectIdInATextColumn_isItsHex() {
        // When
        Object value = ColumnValue.of(new ObjectId("65a1b2c3d4e5f60718293a4b"), "character varying", KOLKATA);

        // Then
        assertThat(value).isEqualTo("65a1b2c3d4e5f60718293a4b");
    }

    @Test
    void of_whenAnIntegerInABigintOrDoubleColumn_isWidened() {
        // When / Then
        assertThat(ColumnValue.of(7, "bigint", KOLKATA)).isEqualTo(7L);
        assertThat(ColumnValue.of(7, "double precision", KOLKATA)).isEqualTo(7.0);
    }

    @Test
    void of_whenADoubleInAnIntegerColumn_isRefused() {
        // When / Then
        assertThatThrownBy(() -> ColumnValue.of(7.5, "integer", KOLKATA)).isInstanceOf(IllegalStateException.class)
                .hasMessage("A Double cannot be stored in a integer column");
    }

    @Test
    void of_whenBinary_isItsBytes() {
        // When
        Object value = ColumnValue.of(new Binary(new byte[] {1, 2, 3}), "bytea", KOLKATA);

        // Then
        assertThat((byte[]) value).containsExactly(1, 2, 3);
    }

    @Test
    void of_whenAColumnTypeTheCopierDoesNotWrite_isRefused() {
        // When / Then
        assertThatThrownBy(() -> ColumnValue.of("{}", "jsonb", KOLKATA)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The copier stores no value in a jsonb column");
    }

    @Test
    void canonical_whenTheSameNumberInAnotherType_isTheSameText() {
        // When / Then
        assertThat(ColumnValue.canonical(7)).isEqualTo("7");
        assertThat(ColumnValue.canonical(7L)).isEqualTo("7");
        assertThat(ColumnValue.canonical(7.0)).isEqualTo("7");
        assertThat(ColumnValue.canonical(null)).isEqualTo("null");
    }
}
