package com.thiru.wealthlens.shared.util.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class FinancialYearTest {

    private static String labelOf(LocalDate date) {
        return FinancialYear.containing(date).label();
    }

    @Test
    @DisplayName("31 March is the last day of the financial year, not the first day of the next")
    void containing_onTheBoundary_belongsToTheYearEnding() {
        // Given / When / Then -- this is the one all three previous copies got wrong
        assertEquals("2023-2024", labelOf(LocalDate.of(2024, 3, 31)));
    }

    @Test
    @DisplayName("the days either side of the boundary")
    void containing_aroundTheBoundary_isCorrect() {
        // Given / When / Then
        assertEquals("2023-2024", labelOf(LocalDate.of(2024, 3, 30)));
        assertEquals("2024-2025", labelOf(LocalDate.of(2024, 4, 1)));
    }

    @Test
    @DisplayName("the year opens on 1 April and runs through the following January to March")
    void containing_acrossTheYear_isCorrect() {
        // Given / When / Then
        assertEquals("2024-2025", labelOf(LocalDate.of(2024, 4, 1)));
        assertEquals("2024-2025", labelOf(LocalDate.of(2024, 12, 31)));
        assertEquals("2024-2025", labelOf(LocalDate.of(2025, 1, 1)));
        assertEquals("2024-2025", labelOf(LocalDate.of(2025, 3, 31)));
        assertEquals("2025-2026", labelOf(LocalDate.of(2025, 4, 1)));
    }

    @Test
    @DisplayName("a leap day is nothing special here")
    void containing_onALeapDay_isCorrect() {
        // Given / When / Then
        assertEquals("2023-2024", labelOf(LocalDate.of(2024, 2, 29)));
    }

    @Test
    void containing_whenTheDateIsNull_isRefused() {
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> FinancialYear.containing(null));
    }

    @Test
    void parse_whenTheLabelIsTwoConsecutiveYears_readsTheYearItStarts() {
        // When
        FinancialYear year = FinancialYear.parse("2025-2026");

        // Then -- 1 April 2025 to 31 March 2026
        assertEquals(2025, year.startYear());
        assertEquals(LocalDate.of(2025, 4, 1), year.startsOn());
        assertEquals(LocalDate.of(2026, 3, 31), year.endsOn());
        assertEquals("2025-2026", year.label());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FY2025-26", "2025-2027", "2025", ""})
    void parse_whenTheLabelIsNotPortfoliosForm_isRefused(String label) {
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> FinancialYear.parse(label));
    }

    @Test
    void positionOf_whenAprilOrMarch_isFirstOrLast() {
        // Given / When / Then
        assertEquals(0, FinancialYear.positionOf(Month.APRIL));
        assertEquals(9, FinancialYear.positionOf(Month.JANUARY));
        assertEquals(11, FinancialYear.positionOf(Month.MARCH));
    }
}
